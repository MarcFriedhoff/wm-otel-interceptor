package com.ibm.webmethods.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.softwareag.is.interceptor.HttpInterceptorException;
import com.softwareag.is.interceptor.HttpInterceptorOutboundIFC;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.Session;
import com.wm.net.HttpContext;
import com.wm.net.HttpHeader;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;

public class OtelHttpInterceptorOutbound implements HttpInterceptorOutboundIFC {

	private final Logger logger = LoggerFactory.getLogger(OtelHttpInterceptorOutbound.class);
	private final ThreadLocal<Scope> scopeThreadLocal;
	private ThreadLocal<Boolean>  skipURL;

	public OtelHttpInterceptorOutbound() {
		this.scopeThreadLocal = new ThreadLocal<>();
		this.skipURL = new ThreadLocal<>();
	}

	@Override
	public void postProcess(int responseCode, String responseMessage, java.util.Map<String, String> headers,
			byte[] bytes, String correlationID) {
		if (Helper.isOtelAgentDisabled)
			return;

		if (skipURL.get())
			return;

		Helper.postProcess(logger, responseCode, responseMessage, headers, correlationID, this.scopeThreadLocal, null);

	}

	@SuppressWarnings("rawtypes")
	@Override
	public void preProcess(String requestURL, String requestType, String httpVersion, java.util.Map headers,
			byte[] streamBytes, String correlationID) throws HttpInterceptorException {
		if (Helper.isOtelAgentDisabled) {
			if (logger.isDebugEnabled())
				logger.debug("TID={} OTEL Java Agent is deactivated. Check env. variable OTEL_JAVAAGENT_ENABLED ",
						Thread.currentThread().getId());
			return;
		}
		if (logger.isDebugEnabled())
			logger.debug(
					"TID={} preProcess: requestType={},requestURL={}, httpVersion={}, headers=[{}], correlationID={}",
					Thread.currentThread().getId(), requestType, requestURL, httpVersion, headers, correlationID);

		skipURL.set(Helper.isSkipURL(logger, requestURL));
		
		if (skipURL.get()) {
			if (logger.isDebugEnabled())
				logger.debug("TID={}, requestURL {} does not match context: {}", Thread.currentThread().getId(),
						requestURL, Helper.context);
			return;
		}

		Session currentSession = InvokeState.getCurrentSession();

		if (currentSession != null) {
			if (logger.isDebugEnabled())
				logger.debug("TID={}, session={}, httpContext(httpclient)={}", Thread.currentThread().getId(),
						currentSession, currentSession.get("(httpclient)"));
			Object ctx = currentSession.get("http-ctx");
			if (ctx instanceof com.wm.net.HttpContext) {
				com.wm.net.HttpContext httpContext = (com.wm.net.HttpContext) ctx;
				// httpContext.getRequestHeader().addField("test-header-http-context",
				// "test-value-http-context-1");

				// Tell OpenTelemetry to inject the context in the HTTP headers
				final TextMapSetter<HttpContext> setter = new TextMapSetter<HttpContext>() {
					@Override
					public void set(HttpContext carrier, String key, String value) {
						if (logger.isDebugEnabled())
							logger.debug("TID={}, set header ({}={})", Thread.currentThread().getId(), key, value);
						// Insert the context as Header
						HttpHeader requestHeader = carrier.getRequestHeader();
						if (requestHeader != null)
							requestHeader.addField(key, value);
						else if (logger.isDebugEnabled())
							logger.debug("TID={}, requestHeader is null.", Thread.currentThread().getId());
					}
				};

				String spanName = "wm-http-outbound";
				if (requestURL != null && requestURL.length() > 0) {
					int i = requestURL.indexOf('?');
					// Do not include query parametes in span name.
					spanName = i > 0 ? String.format("Call %s %s", requestType, requestURL.substring(0, i))
							: String.format("Call %s %s", requestType, requestURL);
				}

				Span outboundSpan = Helper.tracer.spanBuilder(spanName).setSpanKind(SpanKind.CLIENT).startSpan();
				Scope scope = outboundSpan.makeCurrent();
				this.scopeThreadLocal.set(scope);
				// Use the Semantic Conventions.
				// (Note that to set these, Span does not *need* to be the current instance in
				// Context or Scope.)
				outboundSpan.setAttribute("http.request.method", requestType);
				outboundSpan.setAttribute("http.request.url", requestURL);
				// Inject the request with the *current* Context, which contains our current
				// Span.
				GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), httpContext,
						setter);
			} else {
				if (logger.isDebugEnabled())
					logger.debug("TID={}, http-ctx not instance of com.wm.net.HttpContext", Thread.currentThread());
			}

		} else {
			if (logger.isDebugEnabled())
				logger.debug("TID={}, session=null", Thread.currentThread());
		}

	}

}