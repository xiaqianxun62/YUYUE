package com.yuyue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.ErrorCode;
import com.yuyue.dto.AnswerSubmitRequest;
import com.yuyue.dto.QuestionCreateRequest;
import com.yuyue.entity.User;
import com.yuyue.entity.VerificationQuestion;
import com.yuyue.exception.BizException;
import com.yuyue.mapper.UserMapper;
import com.yuyue.mapper.VerificationQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 身份校验服务：
 *   管理员端：问题库 CRUD
 *   用户端：抽题 + 答题（比对答案 → 更新 user.is_verified）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationQuestionMapper questionMapper;
    private final UserMapper userMapper;

    // ========== 管理员：问题库 CRUD ==========

    public List<VerificationQuestion> listAll() {
        return questionMapper.selectList(new LambdaQueryWrapper<VerificationQuestion>()
                .orderByDesc(VerificationQuestion::getEnabled)
                .orderByDesc(VerificationQuestion::getId));
    }

    public VerificationQuestion create(QuestionCreateRequest req) {
        VerificationQuestion q = new VerificationQuestion();
        q.setQuestion(req.getQuestion().trim());
        q.setAnswer(req.getAnswer()); // 原样存，比对时再 trim + toLowerCase
        q.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        questionMapper.insert(q);
        return q;
    }

    public VerificationQuestion update(Long id, QuestionCreateRequest req) {
        VerificationQuestion q = questionMapper.selectById(id);
        if (q == null) throw new BizException(ErrorCode.PARAM_ERROR, "问题不存在");
        q.setQuestion(req.getQuestion().trim());
        q.setAnswer(req.getAnswer());
        q.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        questionMapper.updateById(q);
        return q;
    }

    public void delete(Long id) {
        VerificationQuestion q = questionMapper.selectById(id);
        if (q == null) throw new BizException(ErrorCode.PARAM_ERROR, "问题不存在");
        questionMapper.deleteById(id);
    }

    // ========== 用户端：答题 ==========

    /**
     * 随机抽一道启用中的题（只返回 id + question，不返回 answer）。
     * 如果用户已通过校验，直接抛出业务异常提示。
     */
    public VerificationQuestion randomQuestion(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (user.getIsVerified() != null && user.getIsVerified() == 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "您已通过身份校验，无需重复答题");
        }
        List<VerificationQuestion> enabled = questionMapper.selectList(new LambdaQueryWrapper<VerificationQuestion>()
                .eq(VerificationQuestion::getEnabled, 1));
        if (enabled.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前没有可用的身份校验问题，请联系管理员");
        }
        return enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    /**
     * 提交答案：trim + toLowerCase 比对（多个答案用 | 分隔）。
     * 答对 → user.isVerified = 1，返回 true。
     * 答错 → 返回 false，不更新。
     */
    public boolean submitAnswer(Long userId, AnswerSubmitRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (user.getIsVerified() != null && user.getIsVerified() == 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "您已通过身份校验");
        }
        VerificationQuestion q = questionMapper.selectById(req.getQuestionId());
        if (q == null || q.getEnabled() == null || q.getEnabled() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "问题不存在或已停用");
        }

        String userAnswer = req.getAnswer().trim().toLowerCase();
        String[] validAnswers = q.getAnswer().split("\\|");
        boolean correct = false;
        for (String candidate : validAnswers) {
            if (candidate.trim().toLowerCase().equals(userAnswer)) {
                correct = true;
                break;
            }
        }

        if (correct) {
            user.setIsVerified(1);
            userMapper.updateById(user);
            log.info("用户 {} 通过身份校验", userId);
            return true;
        }
        return false;
    }
}
