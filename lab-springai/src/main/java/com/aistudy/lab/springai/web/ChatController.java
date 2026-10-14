package com.aistudy.lab.springai.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
public class ChatController {

	private final ChatClient chatClient;

	public ChatController(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	/** 同步对话：一次性返回完整回答。Content-Type = text/plain;charset=UTF-8 */
	@GetMapping
	public String chat(@RequestParam("message") String message) {
		return this.chatClient.prompt()
				.user(message)
				.call()
				.content();
	}

	/**
	 * 流式对话：SSE 逐字返回。
	 *
	 * <p>两个必须知道的点：
	 * <ol>
	 *   <li><b>不要在 produces 里写 charset，它会被丢掉。</b>Spring MVC 检测到
	 *       text/event-stream 后由 {@code ReactiveTypeHandler} 创建 {@code SseEmitter}，
	 *       而 SseEmitter 把 content type 硬编码为不带 charset 的 {@code MediaType.TEXT_EVENT_STREAM}，
	 *       produces 里的 charset 参数在那一刻就被覆盖了。因此用浏览器<b>地址栏直接打开本接口</b>
	 *       会按默认编码（ISO-8859-1）解码 → 中文乱码。这不是 Bug，是 SSE 的用法问题：
	 *       正确的消费方式是浏览器的 {@code EventSource}，它按规范强制 UTF-8 解码。
	 *       本地可访问 {@code /} 使用配套的聊天页面。</li>
	 *   <li><b>Flux 结束后必须发一个结束标记。</b>Flux 正常完成时 Spring 只会关闭连接，
	 *       而 EventSource 看到连接关闭会认为"断线"并自动重连（默认 3s），导致请求反复触发。
	 *       所以在末尾 concat 一个 {@code event:done}，前端监听后主动 close()。</li>
	 * </ol>
	 */
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> stream(@RequestParam("message") String message) {

		Flux<ServerSentEvent<String>> content = this.chatClient.prompt()
				.user(message)
				.stream()
				.content()
				.map(chunk -> ServerSentEvent.builder(chunk).build());

		Flux<ServerSentEvent<String>> done = Flux.just(
				ServerSentEvent.<String>builder().event("done").data("").build());

		return content.concatWith(done);
	}

}
