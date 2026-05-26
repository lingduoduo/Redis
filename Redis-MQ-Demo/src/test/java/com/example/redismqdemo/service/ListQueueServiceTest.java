package com.example.redismqdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ListQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @Test
    void pushUsesRpushAndReturnsNewQueueLength() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.rightPush("queue:task", "msg-1")).thenReturn(3L);

        ListQueueService service = new ListQueueService(redisTemplate);

        assertThat(service.push("task", "msg-1")).isEqualTo(3L);
        verify(listOps).rightPush("queue:task", "msg-1");
    }

    @Test
    void popFifoUsesBlpopFromHead() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPop("queue:task", Duration.ofSeconds(5))).thenReturn("msg-1");

        ListQueueService service = new ListQueueService(redisTemplate);

        assertThat(service.popFifo("task", Duration.ofSeconds(5))).isEqualTo("msg-1");
        verify(listOps).leftPop("queue:task", Duration.ofSeconds(5));
    }

    @Test
    void popStackUsesBrpopFromTail() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.rightPop("queue:task", Duration.ofSeconds(5))).thenReturn("msg-last");

        ListQueueService service = new ListQueueService(redisTemplate);

        assertThat(service.popStack("task", Duration.ofSeconds(5))).isEqualTo("msg-last");
        verify(listOps).rightPop("queue:task", Duration.ofSeconds(5));
    }

    @Test
    void popReturnsNullOnTimeout() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPop("queue:empty", Duration.ofSeconds(1))).thenReturn(null);

        ListQueueService service = new ListQueueService(redisTemplate);

        assertThat(service.popFifo("empty", Duration.ofSeconds(1))).isNull();
    }

    @Test
    void sizeUsesLlen() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.size("queue:task")).thenReturn(7L);

        ListQueueService service = new ListQueueService(redisTemplate);

        assertThat(service.size("task")).isEqualTo(7L);
        verify(listOps).size("queue:task");
    }

    @Test
    void fifoOrderRpushBlpop() {
        // Demonstrates FIFO: push A, B, C → pop yields A, B, C in order
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPop("queue:fifo", Duration.ofSeconds(1)))
                .thenReturn("A", "B", "C");

        ListQueueService service = new ListQueueService(redisTemplate);
        service.push("fifo", "A");
        service.push("fifo", "B");
        service.push("fifo", "C");

        assertThat(service.popFifo("fifo", Duration.ofSeconds(1))).isEqualTo("A");
        assertThat(service.popFifo("fifo", Duration.ofSeconds(1))).isEqualTo("B");
        assertThat(service.popFifo("fifo", Duration.ofSeconds(1))).isEqualTo("C");
    }

    @Test
    void lifoOrderRpushBrpop() {
        // Demonstrates LIFO: push A, B, C → pop yields C, B, A in order
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.rightPop("queue:stack", Duration.ofSeconds(1)))
                .thenReturn("C", "B", "A");

        ListQueueService service = new ListQueueService(redisTemplate);
        service.push("stack", "A");
        service.push("stack", "B");
        service.push("stack", "C");

        assertThat(service.popStack("stack", Duration.ofSeconds(1))).isEqualTo("C");
        assertThat(service.popStack("stack", Duration.ofSeconds(1))).isEqualTo("B");
        assertThat(service.popStack("stack", Duration.ofSeconds(1))).isEqualTo("A");
    }

    @Test
    void rejectsInvalidInputs() {
        ListQueueService service = new ListQueueService(redisTemplate);

        assertThatThrownBy(() -> service.push("", "msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queueName cannot be blank");
        assertThatThrownBy(() -> service.push("task", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message cannot be blank");
        assertThatThrownBy(() -> service.popFifo("task", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
        assertThatThrownBy(() -> service.popStack("task", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
    }
}
