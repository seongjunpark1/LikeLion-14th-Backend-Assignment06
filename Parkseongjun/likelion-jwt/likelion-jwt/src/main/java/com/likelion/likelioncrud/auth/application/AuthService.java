package com.likelion.likelioncrud.auth.application;

import com.likelion.likelioncrud.auth.JwtUtil;
import com.likelion.likelioncrud.auth.api.dto.request.LoginRequestDto;
import com.likelion.likelioncrud.auth.api.dto.request.SignupRequestDto;
import com.likelion.likelioncrud.auth.api.dto.request.TokenRefreshRequestDto;
import com.likelion.likelioncrud.auth.api.dto.response.LoginResponseDto;
import com.likelion.likelioncrud.auth.api.dto.response.TokenRefreshResponseDto;
import com.likelion.likelioncrud.auth.domain.RefreshToken;
import com.likelion.likelioncrud.auth.domain.RefreshTokenRepository;
import com.likelion.likelioncrud.common.exception.BusinessException;
import com.likelion.likelioncrud.common.response.code.ErrorCode;
import com.likelion.likelioncrud.member.domain.Member;
import com.likelion.likelioncrud.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본적으로 읽기 전용 트랜잭션 적용 (조회 성능 최적화)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;  // SecurityConfig에서 빈으로 등록한 BCryptPasswordEncoder
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    // application.yml의 jwt.refresh-expiration 값(밀리초)
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // 회원가입
    @Transactional
    public void signup(SignupRequestDto request) {

        // 1. 이메일 중복 체크
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_EMAIL_EXCEPTION,
                    ErrorCode.DUPLICATE_EMAIL_EXCEPTION.getMessage()
            );
        }

        // 2. 비밀번호 BCrypt 암호화 후 Member 생성
        Member member = Member.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))  // 비밀번호 암호화
                .build();

        // 3. DB 저장
        memberRepository.save(member);
    }

    // 로그인
    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {

        // 1. 이메일로 회원 조회 (없으면 예외 처리)
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND_BY_EMAIL_EXCEPTION,
                        ErrorCode.MEMBER_NOT_FOUND_BY_EMAIL_EXCEPTION.getMessage()
                ));

        // 2. 입력한 비밀번호와 암호화된 비밀번호 비교
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PASSWORD_EXCEPTION,
                    ErrorCode.INVALID_PASSWORD_EXCEPTION.getMessage()
            );
        }

        // 3. 인증 성공 후 Access Token 발급
        Long memberId = member.getMemberId();
        String accessToken = jwtUtil.generateToken(memberId);

        // [과제] Refresh Token 발급 및 DB 저장
        // TODO (1): jwtUtil.generateRefreshToken()을 호출해서 refreshToken 문자열을 발급하세요.
        //   로그인한 사용자의 memberId를 담은 refresh token을 생성
        //   access token이 만료됐을 때 이 토큰으로 새 access token을 발급받기 때문
        String refreshToken = jwtUtil.generateRefreshToken(memberId);

        // TODO (2): 기존에 저장된 해당 사용자의 refresh token을 먼저 삭제하세요.
        //           힌트: refreshTokenRepository.deleteByMemberId(...)
        //    기존에 저장된 이 사용자의 refresh token을 삭제
        //    재로그인 시 이전 refresh token이 남아있으면 안 되기 때문
        refreshTokenRepository.deleteByMemberId(memberId);

        // TODO (3): RefreshToken 엔티티를 빌더로 생성하고 DB에 저장하세요.
        //           만료 시각 계산: LocalDateTime.now().plusSeconds(refreshExpiration / 1000)
        //           힌트: refreshTokenRepository.save(RefreshToken.builder()...build())
        //   새로 발급한 refresh token을 DB에 저장
        //   이후 재발급 요청 때 서버가 발급한 토큰인지 확인해야 하기 때문
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(memberId)
                .token(refreshToken)
                .expiredAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .build());

        // 4. Access Token + Refresh Token 반환
        return new LoginResponseDto(accessToken, refreshToken);
    }

    // [과제] Access Token 재발급
    @Transactional
    public TokenRefreshResponseDto reissue(TokenRefreshRequestDto request) {

        // TODO (1): request에서 refreshToken 문자열을 꺼내세요.
        //   클라이언트가 보낸 refresh token을 요청 DTO에서 추출
        //   이 토큰을 기준으로 유효성 검증과 DB 조회를 진행하기 때문
        String refreshToken = request.refreshToken();

        // TODO (2): jwtUtil.validateToken()으로 refresh token의 서명/만료를 검증하세요.
        //           유효하지 않으면 INVALID_REFRESH_TOKEN_EXCEPTION을 throw하세요.
        //   refresh token의 서명과 만료 여부를 검증
        //   위조되었거나 만료된 토큰으로 access token이 재발급되면 안 되기 때문
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFRESH_TOKEN_EXCEPTION,
                    ErrorCode.INVALID_REFRESH_TOKEN_EXCEPTION.getMessage()
            );
        }

        // TODO (3): DB에서 refreshToken 문자열로 RefreshToken 엔티티를 조회하세요.
        //           없으면 INVALID_REFRESH_TOKEN_EXCEPTION을 throw하세요.
        //           힌트: refreshTokenRepository.findByToken(refreshToken).orElseThrow(...)
        //   DB에 저장된 refresh token인지 조회
        //   로그아웃되었거나 재로그인으로 폐기된 토큰은 사용할 수 없어야 하기 때문
        refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REFRESH_TOKEN_EXCEPTION,
                        ErrorCode.INVALID_REFRESH_TOKEN_EXCEPTION.getMessage()
                ));

        // TODO (4): refresh token에서 userId를 추출하고, 새로운 Access Token을 발급하세요.
        //           힌트: jwtUtil.getUserId(refreshToken), jwtUtil.generateToken(userId)
        //   refresh token의 subject에서 사용자 id를 추출
        //   해당 사용자 id로 새로운 access token을 만들어 반환하기 때문
        Long userId = jwtUtil.getUserId(refreshToken);
        String newAccessToken = jwtUtil.generateToken(userId);

        // 5. 새로 발급한 Access Token 반환
        return new TokenRefreshResponseDto(newAccessToken);
    }
}
