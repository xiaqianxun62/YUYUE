package com.yuyue.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 给 Swagger UI 页面注入自动登录脚本（swagger/auto-auth.js）：
 * 调用 /auth/login 后自动提取 JWT 并完成授权，刷新页面后仍保持。
 * <p>
 * 仅开发期使用，生产环境用 {@code yuyue.swagger.auto-auth.enabled=false} 关闭。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yuyue.swagger.auto-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerAutoTokenFilter extends OncePerRequestFilter {

    private static final String SCRIPT_CLASSPATH = "swagger/auto-auth.js";
    private static final String[] TARGET_PAGES = {"/swagger-ui.html", "/swagger-ui/index.html"};

    private final String script;

    public SwaggerAutoTokenFilter() {
        try (InputStream in = new ClassPathResource(SCRIPT_CLASSPATH).getInputStream()) {
            this.script = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("缺少 classpath:" + SCRIPT_CLASSPATH, e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        for (String page : TARGET_PAGES) {
            if (uri.endsWith(page)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        CaptureResponseWrapper wrapper = new CaptureResponseWrapper(response);
        chain.doFilter(request, wrapper);

        byte[] original = wrapper.getCapturedBody();
        String html = new String(original, StandardCharsets.UTF_8);
        String injected = inject(html);
        if (injected == null) {
            // 重定向、压缩过或非 HTML 的响应，原样返回
            writeBack(response, original);
            return;
        }

        byte[] result = injected.getBytes(StandardCharsets.UTF_8);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(result.length);
        writeBack(response, result);
    }

    /**
     * 把脚本插到 &lt;head&gt; 之后：必须早于 swagger-ui-bundle.js 执行，
     * 否则 swagger-client 已经持有原生 fetch 引用，就拦截不到登录响应了。
     *
     * @return 注入后的 HTML；无法注入时返回 null
     */
    private String inject(String html) {
        String scriptTag = "<script>\n" + script + "\n</script>\n";
        int head = html.toLowerCase().indexOf("<head>");
        if (head >= 0) {
            int end = html.indexOf('>', head);
            if (end > 0) {
                return html.substring(0, end + 1) + "\n" + scriptTag + html.substring(end + 1);
            }
        }
        int body = html.toLowerCase().indexOf("<body>");
        if (body >= 0) {
            int end = html.indexOf('>', body);
            if (end > 0) {
                return html.substring(0, end + 1) + "\n" + scriptTag + html.substring(end + 1);
            }
        }
        return null;
    }

    private static void writeBack(HttpServletResponse response, byte[] body) throws IOException {
        if (body.length > 0) {
            response.getOutputStream().write(body);
        }
        response.flushBuffer();
    }

    /** 缓存响应体，便于在页面尾部追加脚本 */
    private static final class CaptureResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        CaptureResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        // 仅同步写出，无需实现
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
            }
            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
        }

        byte[] getCapturedBody() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            return buffer.toByteArray();
        }
    }
}
