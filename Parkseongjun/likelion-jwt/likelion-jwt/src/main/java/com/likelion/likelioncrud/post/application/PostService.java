package com.likelion.likelioncrud.post.application;

import com.likelion.likelioncrud.common.exception.BusinessException;
import com.likelion.likelioncrud.common.response.code.ErrorCode;
import com.likelion.likelioncrud.member.application.MemberService;
import com.likelion.likelioncrud.member.domain.Member;
import com.likelion.likelioncrud.member.domain.Part;
import com.likelion.likelioncrud.member.domain.repository.MemberRepository;
import com.likelion.likelioncrud.post.api.dto.request.PostSaveRequestDto;
import com.likelion.likelioncrud.post.api.dto.request.PostUpdateRequestDto;
import com.likelion.likelioncrud.post.api.dto.response.PostInfoResponseDto;
import com.likelion.likelioncrud.post.api.dto.response.PostListResponseDto;
import com.likelion.likelioncrud.post.domain.Post;
import com.likelion.likelioncrud.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final MemberService memberService;

    // 게시물 저장
    @Transactional
    public void postSave(Long userId, PostSaveRequestDto postSaveRequestDto) {
        Member member = findMemberById(userId);

        checkBackendPermission(member);

        Post post = Post.builder()
                .title(postSaveRequestDto.title())
                .contents(postSaveRequestDto.contents())
                .member(member)
                .build();

        postRepository.save(post);
    }

    // 특정 작성자가 작성한 게시글 목록을 조회
    public Page<PostInfoResponseDto> postFindMember(Long userId, Long memberId, Pageable pageable) {
        // 로그인한 사람의 id를 loginMember 변수에 담음 (요청을 보낸 사람)
        Member loginMember = findMemberById(userId);

        // 그 id가 AI 파트일 경우 게시글 조회 불가(exception 던지기)
        if(loginMember.getPart() == Part.AI) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN_EXCEPTION,
                    ErrorCode.FORBIDDEN_EXCEPTION.getMessage()
            );
        }

        Member member = findMemberById(memberId);
        Page<Post> posts = postRepository.findByMember(member, pageable);
        return posts.map(PostInfoResponseDto::from);
    }

    // 게시물 수정
    @Transactional
    public void postUpdate(Long userId, Long postId, PostUpdateRequestDto postUpdateRequestDto)
    {
        Member member = findMemberById(userId);

        checkBackendPermission(member);

        Post post = findPostById(postId);

        // 로그인한 사용자(BACKEND)가 작성한 게시글인지 검사
        checkPostOwner(member, post);

        post.update(postUpdateRequestDto);
    }

    // 게시물 삭제
    @Transactional
    public void postDelete(Long userId, Long postId) {
        Member member = findMemberById(userId);

        checkBackendPermission(member);

        Post post = findPostById(postId);

        // 로그인한 사용자(BACKEND)가 작성한 게시글인지 검사
        checkPostOwner(member, post);

        postRepository.delete(post);
    }

    private Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND_EXCEPTION,
                        ErrorCode.MEMBER_NOT_FOUND_EXCEPTION.getMessage() + memberId
                ));
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND_EXCEPTION,
                        ErrorCode.POST_NOT_FOUND_EXCEPTION.getMessage() + postId
                ));
    }

    private void checkBackendPermission(Member member) {
        if (member.getPart() != Part.BACKEND) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN_EXCEPTION,
                    ErrorCode.FORBIDDEN_EXCEPTION.getMessage()
            );
        }
    }

    // 로그인한 사용자가 직접 작성한 게시글인지 검사하는 메소드
    // memberId는 DB에서 온 객체값이기 때문에 == 비교 말고 equals() 함수를 사용해서 같은 객체인지 비교함
    private void checkPostOwner(Member member, Post post) {
        if(!post.getMember().getMemberId().equals(member.getMemberId())) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN_EXCEPTION,
                    ErrorCode.FORBIDDEN_EXCEPTION.getMessage()
            );
        }
    }
}
