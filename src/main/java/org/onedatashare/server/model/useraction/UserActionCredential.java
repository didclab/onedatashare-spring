package org.onedatashare.server.model.useraction;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.onedatashare.module.globusapi.EndPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Transient;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActionCredential {
  private String type;
  private String uuid;
  private String name;
  private boolean tokenSaved;
  private String token;

  private EndPoint globusEndpoint;

  @Transient
  private String username;
  @Transient
  private String password;

}
