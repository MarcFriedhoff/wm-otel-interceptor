package com.ibm.webmethods.observability;

import java.util.Map;

import org.slf4j.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class Helper {
	// Regex for requestURL to trace.
	protected static final String context = getPropertyOrEnv("SAG_HTTP_INTERCEPTOR_CTX");
	protected static final String tracerName = getPropertyOrEnv("SAG_HTTP_INTERCEPTOR_TRACER");
	protected static final boolean isOtelAgentDisabled = "false".equals(getPropertyOrEnv("OTEL_JAVAAGENT_ENABLED"));
	protected static final Tracer tracer = GlobalOpenTelemetry.getTracer(
			tracerName == null || tracerName.length() == 0 ? "io.opentelemetry.traces.msr" : tracerName, "1.0");

	private static String getPropertyOrEnv(String propertyName) {
		String value = System.getProperty(propertyName);
		if (value == null) {
			value = System.getenv(propertyName);
		}

		if (value != null) {
			value = value.replaceAll("^\"|\"$", "");
		}

		return value;
	}
	
	public static Boolean isSkipURL(Logger logger, String requestURL) {
		boolean isSkipURL = false;

		if (Helper.context != null && Helper.context.length() > 0) {
			isSkipURL = !URLPatternMatcher.requestURLmatches(Helper.context, requestURL);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("TID=" + Thread.currentThread().getId() + " isSkipURL: requestURL=" + requestURL
					+ ", context=" + Helper.context + ", isSkipURL=" + isSkipURL);
		}
		return isSkipURL;
	}

	/**
	 * 
	 * @param logger
	 * @param responseCode
	 * @param responseMessage
	 * @param headers
	 * @param correlationID
	 * @param scopeThreadLocal
	 * @param scopeExtractedContextThreadLocal
	 */
	public static void postProcess(Logger logger, int responseCode, String responseMessage, Map<String, String> headers,
			String correlationID, ThreadLocal<Scope> scopeThreadLocal,
			ThreadLocal<Scope> scopeExtractedContextThreadLocal) {

		if (logger.isDebugEnabled())
			logger.debug("TID={} postProcess: responseCode={},responseMessage={},  headers=[{}], correlationID={}",
					Thread.currentThread().getId(), responseCode, responseMessage, headers, correlationID);

		Span span = Span.current();
		if (span == null) {
			logger.error("TID={}, span=null");
			return;
		} else {
			if (logger.isDebugEnabled())
				logger.debug("TID={}, span={}", Thread.currentThread().getId(), span);

			span.setAttribute("http.response.code", responseCode);
			span.setAttribute("http.response.message", responseMessage);
			// TODO: add more attributes.
			Scope scope = scopeThreadLocal.get();
			if (scope != null) {
				if (logger.isDebugEnabled())
					logger.debug("TID={}, scope={}", Thread.currentThread().getId(), scope);
				scope.close();
				if (scopeThreadLocal != null)
					scopeThreadLocal.set(null);
			}

			if (scopeExtractedContextThreadLocal != null) {
				Scope scopeExtractedContext = scopeExtractedContextThreadLocal.get();
				if (scopeExtractedContext != null) {
					scopeExtractedContext.close();
					scopeExtractedContextThreadLocal.set(null);
				}
			}
			
			span.end();
		}
	}
}