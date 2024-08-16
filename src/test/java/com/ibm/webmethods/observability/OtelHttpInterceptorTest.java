package com.ibm.webmethods.observability;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class OtelHttpInterceptorTest {

	@Test
	public void testPreProcess() throws Exception {
		// use mockito to check if 
		OtelHttpInterceptor interceptor = new OtelHttpInterceptor();
		interceptor.preProcess("http://localhost:5555", "GET", "HTTP/1.1", null, null, "123");

	}


	@Test	
	public void testPostProcess() throws Exception {
		OtelHttpInterceptor interceptor = new OtelHttpInterceptor();
		interceptor.preProcess("http://localhost:5555", "GET", "HTTP/1.1", null, null, "123");
		interceptor.postProcess(22, "", null, null, "123");
	}


}