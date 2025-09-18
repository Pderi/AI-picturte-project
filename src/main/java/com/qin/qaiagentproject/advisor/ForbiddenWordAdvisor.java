package com.qin.qaiagentproject.advisor;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.common.Message;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ForbiddenWordAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    // 违禁词列表
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "暴力", "色情", "诈骗", "毒品", "赌博"
    );

    // 预编译正则
    private static final Pattern FORBIDDEN_PATTERN = createForbiddenPattern();

    private static Pattern createForbiddenPattern() {
        String regex = FORBIDDEN_WORDS.stream()
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        return Pattern.compile(regex);
    }

    // === 同步处理 ===
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        // 1. 提取并校验用户输入
        validateInput(extractUserMessages(advisedRequest));
        // 2. 调用处理链
        AdvisedResponse response = chain.nextAroundCall(advisedRequest);

        // 3. 提取并校验AI响应

        validateOutput(extractAiOutput(response));
        return response;
    }

    // === 流式处理 ===
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // 1. 提取并校验用户输入
       validateInput(extractUserMessages(advisedRequest));

        // 2. 调用处理链并处理流式响应
        return chain.nextAroundStream(advisedRequest)
                .map(response -> {
                    // 3. 对每个流片段进行校验
                    validateOutput(extractAiOutput(response));
                    return response;
                });
    }

    // === 执行顺序设置 ===
    @Override
    public int getOrder() {
        // 设置较高优先级（值越小优先级越高）
        return 100;
    }

    // === 唯一标识符 ===
    @Override
    public String getName() {
        return "ForbiddenWordValidationAdvisor";
    }

    // === 提取用户消息 ===
    private String extractUserMessages(AdvisedRequest request) {

        return request.userText();
    }

    // === 提取AI输出 ===
    private String extractAiOutput(AdvisedResponse response) {
       return response.response().getResult().getOutput().getText();
    }

    // === 验证方法 ===
    private void validateInput(String input) {
        if (input == null || input.isEmpty()) return;

        if (FORBIDDEN_PATTERN.matcher(input).find()) {
            throw new SecurityException("输入包含违禁词: " + input);
        }
    }

    private void validateOutput(String output) {
        if (output == null || output.isEmpty()) return;

        if (FORBIDDEN_PATTERN.matcher(output).find()) {
            throw new SecurityException("AI响应包含违禁词: " + output);
        }
    }
}