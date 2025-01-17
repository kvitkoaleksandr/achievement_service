package faang.school.achievement.service.handler.eventHandlerImpl.commentEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import faang.school.achievement.dto.AchievementProgressDto;
import faang.school.achievement.dto.event.CommentEvent;
import faang.school.achievement.exception.NotFoundException;
import faang.school.achievement.mapper.AchievementMapperImpl;
import faang.school.achievement.model.Achievement;
import faang.school.achievement.model.AchievementProgress;
import faang.school.achievement.service.publisher.AchievementPublisher;
import faang.school.achievement.service.AchievementCache;
import faang.school.achievement.service.AchievementService;
import faang.school.achievement.service.handler.eventHandler.commentEvent.EvilCommenterAchievementHandler;
import faang.school.achievement.service.handler.eventHandler.commentEvent.CommentEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvilCommenterAchievementHandlerTest {
    @Mock
    private AchievementService achievementService;
    @Mock
    private AchievementCache achievementCache;
    @Mock
    private AchievementPublisher achievementPublisher;
    @Spy
    private AchievementMapperImpl mapper;
    @Captor
    private ArgumentCaptor<AchievementProgress> progressCaptor;
    private CommentEventHandler handler;
    private final String nameAchievement = "EVIL_COMMENTER";

    private CommentEvent commentEvent;
    private Achievement achievement;
    private AchievementProgress achievementProgress;
    private AchievementProgressDto achievementProgressDto;


    @BeforeEach
    void setUp() {
        prepareData();
        handler = new EvilCommenterAchievementHandler(achievementCache, achievementService, achievementPublisher,
                nameAchievement, mapper);
    }

    @Test
    void testProcessWithNonExistsAchievement() {
        // given
        doThrow(NotFoundException.class).when(achievementCache).get(Mockito.anyString());
        // then
        assertThrows(NotFoundException.class, () -> handler.process(commentEvent));
    }

    @Test
    void testProcessWithAlreadyGotAchievement() throws JsonProcessingException {
        // given
        boolean achievementWasReceived = true;
        when(achievementCache.get(nameAchievement)).thenReturn(achievement);
        when(achievementService.hasAchievement(commentEvent.getUserId(), achievement.getId())).thenReturn(achievementWasReceived);
        // when
        handler.process(commentEvent);
        // then
        verify(achievementService, times(0)).createProgressIfNecessary(commentEvent.getUserId(), achievement.getId());
    }

    @Test
    void testProcessWithAchievementProgressUpdate() throws JsonProcessingException {
        // given
        boolean achievementWasReceived = false;
        when(achievementCache.get(nameAchievement)).thenReturn(achievement);
        when(achievementService.hasAchievement(commentEvent.getUserId(), achievement.getId())).thenReturn(achievementWasReceived);
        when(achievementService.getProgress(commentEvent.getUserId(), achievement.getId())).thenReturn(achievementProgressDto);
        int progressPointExp = 1;
        // when
        handler.process(commentEvent);
        // then
        verify(achievementService, times(1)).saveProgress(progressCaptor.capture());
        assertEquals(progressPointExp, progressCaptor.getValue().getCurrentPoints());
    }

    @Test
    void testProcessWithReceivingAchievement() throws JsonProcessingException {
        // given
        boolean achievementWasReceived = false;
        when(achievementCache.get(nameAchievement)).thenReturn(achievement);
        when(achievementService.hasAchievement(commentEvent.getUserId(), achievement.getId())).thenReturn(achievementWasReceived);
        achievementProgressDto.setCurrentPoints(99);
        when(achievementService.getProgress(commentEvent.getUserId(), achievement.getId())).thenReturn(achievementProgressDto);
        // when
        handler.process(commentEvent);
        // then
        verify(achievementService, times(1)).deleteAchievementProgress(achievementProgress.getId());
        verify(achievementService, times(1)).giveAchievement(commentEvent.getUserId(), achievement);
        verify(achievementPublisher, times(1)).publish(Mockito.any());
    }

    private void prepareData() {
        commentEvent = CommentEvent.builder()
                .id(1L)
                .authorId(2L)
                .postId(3L)
                .content("content")
                .build();

        achievement = Achievement.builder()
                .id(9L)
                .title(nameAchievement)
                .points(100)
                .build();

        achievementProgress = AchievementProgress.builder()
                .id(4L)
                .userId(commentEvent.getAuthorId())
                .currentPoints(0)
                .achievement(achievement)
                .build();

        achievementProgressDto = mapper.toAchievementProgressDto(achievementProgress);
    }
}