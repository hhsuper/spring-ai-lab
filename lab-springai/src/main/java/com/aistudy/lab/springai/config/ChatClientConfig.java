package com.aistudy.lab.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v0.1：装配一个带默认系统提示词的 ChatClient。
 * 后续版本（记忆 / RAG / 工具调用）的 Advisor 都挂在这里。
 */
@Configuration
public class ChatClientConfig {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder
				.defaultSystem("""
						你是一名严谨的中文技术助手。
						要求：用简体中文回答；结论先行；不确定时明确说明不确定，不要编造。
						""")
				.build();
	}

}
