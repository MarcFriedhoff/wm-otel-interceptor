package com.ibm.webmethods.observability;

import java.util.regex.Pattern;

public class URLPatternMatcher {

    public static void main (String[] args) {
		String _context = System.getProperty("SAG_HTTP_INTERCEPTOR_CTX");
		// check if there is at least one argument
		if (args.length < 1) {
			System.out.println("Usage: java -cp <classpath> com.ibm.webmethods.observability.URLPatternMatcher <requestURL> <context|environment var: SAG_HTTP_INTERCEPTOR_CTX>");
			System.exit(1);
		}

		// get first argument as requestURL
		String _requestURL = args[0];
		// check if there is a second argument
		if (args.length == 2) {
			// get second argument as context
			_context = args[1];
		}
		
        if (_context == null || _context.length() == 0) {
            System.out.println("Context is not set. Please set the environment variable SAG_HTTP_INTERCEPTOR_CTX or provide context in argument.");
            System.exit(1);
        }

		// test if requestURL is skipped
		System.out.println("Request URL: "+ _requestURL + " matches pattern: " + _context + ":" + URLPatternMatcher.requestURLmatches(_context, _requestURL));
	}

    public static boolean requestURLmatches(String context, String requestURL) {
    	return Pattern.compile(context).matcher(requestURL).matches();
    }
    
}
