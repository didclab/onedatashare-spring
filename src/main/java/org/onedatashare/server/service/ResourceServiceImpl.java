package org.onedatashare.server.service;

import org.onedatashare.server.controller.UploadCredential;
import org.onedatashare.server.model.core.*;
import org.onedatashare.server.model.credential.UserInfoCredential;
import org.onedatashare.server.model.useraction.UserAction;
import org.onedatashare.server.model.useraction.UserActionResource;
import org.onedatashare.server.module.clientupload.ClientUploadSession;
import org.onedatashare.server.module.dropbox.DbxSession;
import org.onedatashare.server.module.vfs.VfsSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

@Service
public class ResourceServiceImpl implements ResourceService<Resource> {
    @Autowired
    private UserService userService;

    @Autowired
    private JobService jobService;

    private HashMap<UUID, Disposable> ongoingJobs = new HashMap<>();

    public Mono<Resource> getResourceWithUserActionUri(String cookie, UserAction userAction) {
        final String path = pathFromUri(userAction.uri);
        return userService.getLoggedInUser(cookie)
                .map(user -> new UserInfoCredential(userAction.credential))
                .map(credential -> new VfsSession(URI.create(userAction.uri), credential))
                .flatMap(VfsSession::initialize)
                .flatMap(vfsSession -> vfsSession.select(path));
    }

    public Mono<Resource> getResourceWithUserActionResource(String cookie, UserActionResource userActionResource) {
        final String path = pathFromUri(userActionResource.uri);
        return userService.getLoggedInUser(cookie)
                .map(user -> createCredential(userActionResource, user))
                .map(credential -> createSession(userActionResource.uri, credential))
                .flatMap(session -> session.initialize())
                .flatMap(session -> ((Session)session).select(path));
    }

    public String pathFromUri(String uri) {
        String path = "";
        if(uri.contains("dropbox://")){
            path = uri.split("dropbox://")[1];
        }
        else path = uri;
        try {
            path = java.net.URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return path;
    }

    public Credential createCredential(UserActionResource userActionResource, User user) {
        if(userActionResource.uri.contains("dropbox://")){
            return user.getCredentials().get(UUID.fromString(userActionResource.credential.uuid));
        }else if(userActionResource.uri.contains("Upload")){
            return userActionResource.uploader;
        }
        else return new UserInfoCredential(userActionResource.credential);
    }

    public Session createSession(String uri, Credential credential) {
        if(uri.contains("dropbox://")){
            return new DbxSession(URI.create(uri), credential);
        }else if(uri.contains("Upload")){
            UploadCredential upc = (UploadCredential)credential;
            return new ClientUploadSession(upc.getFux(), upc.getSize(), upc.getName());
        }
        else return new VfsSession(URI.create(uri), credential);
    }

    public Mono<Stat> list(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction).flatMap(Resource::stat);
    }

    public Mono<Stat> mkdir(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction)
                .flatMap(Resource::mkdir)
                .flatMap(resource -> ((Resource)resource).stat());
    }

    public Mono<Resource> delete(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction)
                .flatMap(Resource::delete);
    }

    public Mono<Job> submit(String cookie, UserAction userAction) {
        return userService.getLoggedInUser(cookie)
            .map(user -> {
                Job job = new Job(userAction.src, userAction.dest);
                job.setStatus(JobStatus.scheduled);
                job = user.saveJob(job);
                userService.saveUser(user).subscribe();
                return job;
            })
            .flatMap(jobService::saveJob)
            .doOnSuccess(job -> processTransferFromJob(job, cookie))
            .subscribeOn(Schedulers.elastic());
    }

    public Mono<Job> restartJob(String cookie, UserAction userAction){
        return userService.getLoggedInUser(cookie)
            .flatMap(user ->{
                return jobService.findJobByJobId(cookie, userAction.job_id)
                    .map(job -> {
                        Job restartedJob = new Job(job.src, job.dest);
                        restartedJob.setStatus(JobStatus.scheduled);
                        restartedJob = user.saveJob(restartedJob);
                        userService.saveUser(user).subscribe();
                        return restartedJob;
                    })
                    .flatMap(jobService::saveJob)
                    .doOnSuccess(restartedJob -> processTransferFromJob(restartedJob, cookie));
            })
            .subscribeOn(Schedulers.elastic());
    }

    public Mono<Job> cancel(String cookie, UserAction userAction) {
        return userService.getLoggedInUser(cookie)
                .flatMap(user -> {
                    return jobService.findJobByJobId(cookie, userAction.job_id)
                            .map(job -> {
                                ongoingJobs.get(job.uuid).dispose();
                                ongoingJobs.remove(job.uuid);
                                return job.setStatus(JobStatus.removed);
                            });
                })
                .subscribeOn(Schedulers.elastic());
    }

    public void processTransferFromJob(Job job, String cookie) {
        Transfer<Resource, Resource> transfer = new Transfer<>();
        Disposable ongoingJob = getResourceWithUserActionResource(cookie, job.src)
            .map(transfer::setSource)
            .flatMap(t -> getResourceWithUserActionResource(cookie, job.dest))
            .map(transfer::setDestination)
            .flux()
            .flatMap(transfer1 -> transfer1.start(1L << 20))
            .doOnSubscribe(s -> job.setStatus(JobStatus.processing))
            .doOnCancel(new RunnableCanceler(job))
            .doFinally(s -> {
                if (job.getStatus() != JobStatus.removed)
                    job.setStatus(JobStatus.complete);
                jobService.saveJob(job).subscribe();
            })
            .map(job::updateJobWithTransferInfo)
            .flatMap(jobService::saveJob)
            .subscribe();
        ongoingJobs.put(job.uuid, ongoingJob);
    }

    class RunnableCanceler implements Runnable {
        Job job;

        public RunnableCanceler(Job job) {
            this.job = job;
        }

        @Override
        public void run() {
            job.setStatus(JobStatus.removed);
//            ongoingJobs.remove(job.uuid);
        }
    }
}
