/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.service;

import com.google.cloud.healthcare.fdamystudies.beans.AuditLogEventRequest;
import com.google.cloud.healthcare.fdamystudies.beans.AuthUserRequest;
import com.google.cloud.healthcare.fdamystudies.beans.DeactivateAccountResponse;
import com.google.cloud.healthcare.fdamystudies.beans.SetUpAccountRequest;
import com.google.cloud.healthcare.fdamystudies.beans.SetUpAccountResponse;
import com.google.cloud.healthcare.fdamystudies.beans.UpdateEmailStatusRequest;
import com.google.cloud.healthcare.fdamystudies.beans.UpdateEmailStatusResponse;
import com.google.cloud.healthcare.fdamystudies.beans.UserProfileRequest;
import com.google.cloud.healthcare.fdamystudies.beans.UserProfileResponse;
import com.google.cloud.healthcare.fdamystudies.beans.UserResponse;
import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.common.MessageCode;
import com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerAuditLogHelper;
import com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent;
import com.google.cloud.healthcare.fdamystudies.common.UserAccountStatus;
import com.google.cloud.healthcare.fdamystudies.common.UserStatus;
import com.google.cloud.healthcare.fdamystudies.config.AppPropertyConfig;
import com.google.cloud.healthcare.fdamystudies.mapper.UserProfileMapper;
import com.google.cloud.healthcare.fdamystudies.model.UserRegAdminEntity;
import com.google.cloud.healthcare.fdamystudies.repository.UserRegAdminRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class UserProfileServiceImpl implements UserProfileService {

  private XLogger logger = XLoggerFactory.getXLogger(StudyServiceImpl.class.getName());

  @Autowired private UserRegAdminRepository userRegAdminRepository;

  @Autowired private AppPropertyConfig appPropertyConfig;

  @Autowired private RestTemplate restTemplate;

  @Autowired private OAuthService oauthService;

  @Autowired private ParticipantManagerAuditLogHelper participantManagerHelper;

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getUserProfile(String userId) {
    logger.entry("begin getUserProfile()");

    Optional<UserRegAdminEntity> optUserRegAdminUser =
        userRegAdminRepository.findByUrAdminAuthId(userId);

    if (!optUserRegAdminUser.isPresent()) {
      logger.exit(ErrorCode.USER_NOT_EXISTS);
      return new UserProfileResponse(ErrorCode.USER_NOT_EXISTS);
    }

    UserRegAdminEntity adminUser = optUserRegAdminUser.get();
    if (!adminUser.isActive()) {
      logger.exit(ErrorCode.USER_NOT_ACTIVE);
      return new UserProfileResponse(ErrorCode.USER_NOT_ACTIVE);
    }

    UserProfileResponse userProfileResponse =
        UserProfileMapper.toUserProfileResponse(adminUser, MessageCode.GET_USER_PROFILE_SUCCESS);
    logger.exit(userProfileResponse.getMessage());
    return userProfileResponse;
  }

  @Override
  @Transactional
  public UserProfileResponse updateUserProfile(
      UserProfileRequest userProfileRequest, AuditLogEventRequest aleRequest) {
    logger.entry("begin updateUserProfile()");

    Optional<UserRegAdminEntity> optUserRegAdminUser =
        userRegAdminRepository.findById(userProfileRequest.getUserId());

    if (!optUserRegAdminUser.isPresent()) {
      participantManagerHelper.logEvent(
          ParticipantManagerEvent.USER_ACCOUNT_UPDATED_FAILED, aleRequest, null);
      logger.exit(ErrorCode.USER_NOT_EXISTS);
      return new UserProfileResponse(ErrorCode.USER_NOT_EXISTS);
    }

    UserRegAdminEntity adminUser = optUserRegAdminUser.get();
    if (!adminUser.isActive()) {
      participantManagerHelper.logEvent(
          ParticipantManagerEvent.USER_ACCOUNT_UPDATED_FAILED, aleRequest, null);
      logger.exit(ErrorCode.USER_NOT_ACTIVE);
      return new UserProfileResponse(ErrorCode.USER_NOT_ACTIVE);
    }

    adminUser = UserProfileMapper.fromUserProfileRequest(userProfileRequest);
    adminUser = userRegAdminRepository.saveAndFlush(adminUser);

    UserProfileResponse profileResponse =
        new UserProfileResponse(MessageCode.PROFILE_UPDATE_SUCCESS);
    profileResponse.setUserId(adminUser.getId());
    participantManagerHelper.logEvent(
        ParticipantManagerEvent.USER_ACCOUNT_UPDATED, aleRequest, null);
    logger.exit(MessageCode.PROFILE_UPDATE_SUCCESS);
    return profileResponse;
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse findUserProfileBySecurityCode(String securityCode) {
    logger.entry("begin getUserProfileWithSecurityCode()");

    Optional<UserRegAdminEntity> optUserRegAdminUser =
        userRegAdminRepository.findBySecurityCode(securityCode);

    if (!optUserRegAdminUser.isPresent()) {
      logger.exit(ErrorCode.INVALID_SECURITY_CODE);
      return new UserProfileResponse(ErrorCode.INVALID_SECURITY_CODE);
    }

    UserRegAdminEntity user = optUserRegAdminUser.get();
    Timestamp now = new Timestamp(Instant.now().toEpochMilli());

    if (now.after(user.getSecurityCodeExpireDate())) {
      logger.exit(ErrorCode.SECURITY_CODE_EXPIRED);
      return new UserProfileResponse(ErrorCode.SECURITY_CODE_EXPIRED);
    }

    UserProfileResponse userProfileResponse =
        UserProfileMapper.toUserProfileResponse(
            user, MessageCode.GET_USER_PROFILE_WITH_SECURITY_CODE_SUCCESS);
    logger.exit(String.format("message=%s", userProfileResponse.getMessage()));
    return userProfileResponse;
  }

  @Override
  @Transactional
  public SetUpAccountResponse saveUser(
      SetUpAccountRequest setUpAccountRequest, AuditLogEventRequest aleRequest) {
    logger.entry("saveUser");

    Optional<UserRegAdminEntity> optUsers =
        userRegAdminRepository.findByEmail(setUpAccountRequest.getEmail());

    if (!optUsers.isPresent()) {
      return new SetUpAccountResponse(ErrorCode.USER_NOT_INVITED);
    }

    // Bad request and errors handled in RestResponseErrorHandler class
    UserResponse authRegistrationResponse = registerUserInAuthServer(setUpAccountRequest);

    Map<String, String> map =
        Stream.of(
                new String[][] {
                  {"user_id", optUsers.get().getId()},
                  {"account_status", String.valueOf(setUpAccountRequest.getStatus())}
                })
            .collect(Collectors.toMap(data -> data[0], data -> data[1]));

    if (!StringUtils.equals(authRegistrationResponse.getCode(), "201")) {
      participantManagerHelper.logEvent(
          ParticipantManagerEvent.NEW_USER_ACCOUNT_ACTIVATION_FAILURE, aleRequest, map);
      return new SetUpAccountResponse(ErrorCode.REGISTRATION_FAILED_IN_AUTH_SERVER);
    }
    UserRegAdminEntity userRegAdminUser = optUsers.get();
    userRegAdminUser.setUrAdminAuthId(authRegistrationResponse.getUserId());
    userRegAdminUser.setFirstName(setUpAccountRequest.getFirstName());
    userRegAdminUser.setLastName(setUpAccountRequest.getLastName());
    userRegAdminUser.setStatus(UserStatus.ACTIVE.getValue());
    userRegAdminUser = userRegAdminRepository.saveAndFlush(userRegAdminUser);

    SetUpAccountResponse setUpAccountResponse =
        new SetUpAccountResponse(
            userRegAdminUser.getId(),
            authRegistrationResponse.getTempRegId(),
            authRegistrationResponse.getUserId(),
            MessageCode.SET_UP_ACCOUNT_SUCCESS);

    logger.exit(MessageCode.SET_UP_ACCOUNT_SUCCESS);
    return setUpAccountResponse;
  }

  private UserResponse registerUserInAuthServer(SetUpAccountRequest setUpAccountRequest) {
    logger.entry("registerUserInAuthServer()");

    AuthUserRequest userRequest =
        new AuthUserRequest(
            "PARTICIPANT MANAGER",
            setUpAccountRequest.getEmail(),
            setUpAccountRequest.getPassword(),
            UserAccountStatus.ACTIVE.getStatus());

    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer " + oauthService.getAccessToken());

    HttpEntity<AuthUserRequest> requestEntity = new HttpEntity<>(userRequest, headers);

    ResponseEntity<UserResponse> response =
        restTemplate.postForEntity(
            appPropertyConfig.getAuthRegisterUrl(), requestEntity, UserResponse.class);

    logger.exit(String.format("status=%d", response.getStatusCodeValue()));
    return response.getBody();
  }

  @Override
  public DeactivateAccountResponse deactivateAccount(String userId) {
    logger.entry("deactivateAccount()");

    Optional<UserRegAdminEntity> optUserRegAdmin = userRegAdminRepository.findById(userId);
    if (!optUserRegAdmin.isPresent()) {
      return new DeactivateAccountResponse(ErrorCode.USER_NOT_FOUND);
    }

    UserRegAdminEntity userRegAdmin = optUserRegAdmin.get();

    deactivateUserInAuthServer(userRegAdmin.getUrAdminAuthId());

    userRegAdmin.setStatus(UserStatus.DEACTIVATED.getValue());
    userRegAdminRepository.saveAndFlush(userRegAdmin);

    logger.exit(MessageCode.DEACTIVATE_USER_SUCCESS);
    return new DeactivateAccountResponse(MessageCode.DEACTIVATE_USER_SUCCESS);
  }

  private UpdateEmailStatusResponse deactivateUserInAuthServer(String authUserId) {
    logger.entry("updateUserInfoInAuthServer()");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("Authorization", "Bearer " + oauthService.getAccessToken());

    UpdateEmailStatusRequest emailStatusRequest = new UpdateEmailStatusRequest();
    emailStatusRequest.setStatus(UserAccountStatus.DEACTIVATED.getStatus());

    HttpEntity<UpdateEmailStatusRequest> request = new HttpEntity<>(emailStatusRequest, headers);

    ResponseEntity<UpdateEmailStatusResponse> responseEntity =
        restTemplate.exchange(
            appPropertyConfig.getAuthServerUpdateStatusUrl(),
            HttpMethod.PUT,
            request,
            UpdateEmailStatusResponse.class,
            authUserId);

    // Bad request and errors handled in RestResponseErrorHandler class
    UpdateEmailStatusResponse updateEmailResponse = responseEntity.getBody();

    logger.exit(String.format("status=%d", updateEmailResponse.getHttpStatusCode()));
    return updateEmailResponse;
  }
}
