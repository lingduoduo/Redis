package com.example.redisrankdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SocialGraphServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;

    @Test
    void followAddsToBothFollowAndFansSets() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("u1001:follow", "u1002")).thenReturn(1L);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.follow("u1001", "u1002")).isTrue();
        verify(setOps).add("u1001:follow", "u1002");
        verify(setOps).add("u1002:fans", "u1001");
    }

    @Test
    void followReturnsFalseForDuplicateFollow() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("u1001:follow", "u1002")).thenReturn(0L);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.follow("u1001", "u1002")).isFalse();
    }

    @Test
    void unfollowRemovesFromBothFollowAndFansSets() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("u1001:follow", (Object) "u1002")).thenReturn(1L);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.unfollow("u1001", "u1002")).isTrue();
        verify(setOps).remove("u1001:follow", (Object) "u1002");
        verify(setOps).remove("u1002:fans", (Object) "u1001");
    }

    @Test
    void unfollowReturnsFalseWhenRelationshipAbsent() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("u1001:follow", (Object) "u9999")).thenReturn(0L);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.unfollow("u1001", "u9999")).isFalse();
    }

    @Test
    void isFollowingUsesSismember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("u1001:follow", "u1002")).thenReturn(true);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.isFollowing("u1001", "u1002")).isTrue();
        verify(setOps).isMember("u1001:follow", "u1002");
    }

    @Test
    void followingUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("u1001:follow")).thenReturn(Set.of("u1002", "u1003", "u1004"));

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.following("u1001")).containsExactlyInAnyOrder("u1002", "u1003", "u1004");
        verify(setOps).members("u1001:follow");
    }

    @Test
    void fansUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("u1002:fans")).thenReturn(Set.of("u1001", "u1003", "u1004", "u1005"));

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.fans("u1002")).containsExactlyInAnyOrder("u1001", "u1003", "u1004", "u1005");
        verify(setOps).members("u1002:fans");
    }

    @Test
    void followCountUsesScard() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("u1001:follow")).thenReturn(3L);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.followCount("u1001")).isEqualTo(3L);
        verify(setOps).size("u1001:follow");
    }

    @Test
    void isMutualReturnsTrueWhenBothFollow() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("u1001:follow", "u1002")).thenReturn(true);
        when(setOps.isMember("u1002:follow", "u1001")).thenReturn(true);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.isMutual("u1001", "u1002")).isTrue();
    }

    @Test
    void isMutualReturnsFalseForOneWayFollow() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("u1001:follow", "u1004")).thenReturn(true);
        when(setOps.isMember("u1004:follow", "u1001")).thenReturn(false);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.isMutual("u1001", "u1004")).isFalse();
    }

    @Test
    void commonFollowingUsesSinter() {
        // SINTER u1001:follow u1002:fans → {u1003, u1004}
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect("u1001:follow", "u1002:fans")).thenReturn(Set.of("u1003", "u1004"));

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.commonFollowing("u1001", "u1002"))
                .containsExactlyInAnyOrder("u1003", "u1004");
        verify(setOps).intersect("u1001:follow", "u1002:fans");
    }

    @Test
    void commonFollowingReturnsEmptySetWhenNoOverlap() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect("u1001:follow", "u9999:fans")).thenReturn(Set.of());

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.commonFollowing("u1001", "u9999")).isEmpty();
    }

    @Test
    void mayKnowUsesSdiff() {
        // SDIFF u1002:follow u1001:follow → {u1001, u1005}  (u1001 may know u1005 via u1002)
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.difference("u1002:follow", "u1001:follow")).thenReturn(Set.of("u1001", "u1005"));

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.mayKnow("u1001", "u1002")).containsExactlyInAnyOrder("u1001", "u1005");
        verify(setOps).difference("u1002:follow", "u1001:follow");
    }

    @Test
    void mayKnowReturnsEmptySetWhenNoNewConnections() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.difference("u1002:follow", "u1001:follow")).thenReturn(Set.of());

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.mayKnow("u1001", "u1002")).isEmpty();
    }

    @Test
    void followingReturnsEmptySetWhenNull() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("u9999:follow")).thenReturn(null);

        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.following("u9999")).isEmpty();
    }

    @Test
    void rejectsInvalidInputs() {
        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThatThrownBy(() -> service.follow(null, "u1002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.follow("u1001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.unfollow("u1001", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.commonFollowing(null, "u1002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.mayKnow("u1001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
    }

    @Test
    void followKeyAndFansKeyFormats() {
        SocialGraphService service = new SocialGraphService(redisTemplate);

        assertThat(service.followKey("u1001")).isEqualTo("u1001:follow");
        assertThat(service.fansKey("u1002")).isEqualTo("u1002:fans");
    }
}
