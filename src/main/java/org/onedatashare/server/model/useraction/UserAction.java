package org.onedatashare.server.model.useraction;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.onedatashare.module.globusapi.EndPoint;
import org.onedatashare.server.model.requestdata.DeleteRequestData;
import org.onedatashare.server.model.requestdata.ListRequestData;

import java.util.ArrayList;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAction {
    private String action;
    private String email;
    private String firstName;
    private String lastName;
    private String organization;
    private String password;
    private String uri;
    private String id;
    private ArrayList<IdMap> map;
    private String type;
    private String uuid;
    private String code;
    private String confirmPassword;
    private String newPassword;
    private UserActionResource src;
    private UserActionResource dest;
    private UserActionCredential credential;
    private Integer job_id;

    private boolean saveOAuth;
    private String filter_fulltext;
    private EndPoint globusEndpoint;
    private String username;
    private boolean isAdmin;

    private int pageNo;
    private int pageSize;
    private String sortBy;
    private String sortOrder;

    private String portNumber;
    private String captchaVerificationValue;

    public static UserAction convertToUserAction(ListRequestData listReqData){
        UserAction ua = new UserAction();
        ua.setUri(listReqData.getUri());
        ua.setId(listReqData.getId());
        ua.setPortNumber(listReqData.getPortNumber());
        ua.setType(listReqData.getType());
        ua.setCredential(listReqData.getCredential());
        return ua;
    }

    public static UserAction convertToUserAction(DeleteRequestData deleteRequestData){
        UserAction ua = new UserAction();
        ua.setUri(deleteRequestData.getUri());
        ua.setId(deleteRequestData.getId());
        ua.setPortNumber(deleteRequestData.getPortNumber());
        ua.setType(deleteRequestData.getType());
        ua.setCredential(deleteRequestData.getCredential());
        ua.setMap(deleteRequestData.getMap());
        return ua;
    }

}
