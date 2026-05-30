package com.likelion.likelioncrud.kakao.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.likelion.likelioncrud.common.exception.BusinessException;
import com.likelion.likelioncrud.common.response.code.ErrorCode;

public record KakaoUserInfoResponse(

        Long id,

        @JsonProperty("properties")
        Properties properties,

        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {

    public record Properties(
            String nickname
    ){

    }

    public record KakaoAccount(
            String email
    ){

    }

    public String getNickname() {
        if(properties == null || properties.nickname() == null) {
            return "카카오사용자";
        }

        return properties.nickname();
    }

    public String getEmail() {
        /*
        * TODO
        카카오 동의 항목에서 이메일 권한을 설정한 뒤,
        kakaoAccount.email()을 반환하도록 수정해보세요
         */
        if (kakaoAccount == null || kakaoAccount.email() == null || kakaoAccount.email().isBlank()) {
            throw new BusinessException(
                    ErrorCode.KAKAO_EMAIL_NOT_FOUND_EXCEPTION,
                    ErrorCode.KAKAO_EMAIL_NOT_FOUND_EXCEPTION.getMessage()
            );
        }

        return kakaoAccount.email();
    }
}
