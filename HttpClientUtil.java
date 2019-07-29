package com.suning.http;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 功能描述: http 请求的工具类
 * 
 * @version 2.0.0
 * @author yuruige
 */
public class HttpClientUtil {
	
	private HttpClientUtil(){}
	
	private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

	private static volatile CloseableHttpClient httpClient = null;

	/**
	 * 功能描述:
	 * @return CloseableHttpClient
	 * @version 2.0.0
	 * @author yuruige
	 */
	public static CloseableHttpClient getHttpClient() {
		if (httpClient == null) {
			synchronized (HttpClientUtil.class) {
				if (httpClient == null) {
					Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
							.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.INSTANCE)
							.register("https", SSLConnectionSocketFactory.getSystemSocketFactory()).build();

					PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
					manager.setMaxTotal(300);
					manager.setDefaultMaxPerRoute(100);
					manager.setValidateAfterInactivity(4000);

					RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(2000)
							.setConnectionRequestTimeout(2000).build();
					httpClient = HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(requestConfig)
							.setConnectionTimeToLive(60, TimeUnit.SECONDS).setConnectionManagerShared(false)
							.evictIdleConnections(60, TimeUnit.SECONDS).evictExpiredConnections()
							.setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
							.setKeepAliveStrategy((HttpResponse response, HttpContext context) -> {
									HeaderElementIterator it = new BasicHeaderElementIterator(
											response.headerIterator("Keep-Alive"));
									while (it.hasNext()) {
										HeaderElement he = it.nextElement();
										String param = he.getName();
										String value = he.getValue();
										if ((value != null) && (param.equalsIgnoreCase("timeout"))) {
											try {
												return Long.parseLong(value) * 1000L;
											} catch (NumberFormatException ignore) {
											}
										}
									}
									return 15L;
							}).setRetryHandler(DefaultHttpRequestRetryHandler.INSTANCE)
							.build();
					
					Runtime.getRuntime().addShutdownHook(new Thread(() -> {
						try {
							httpClient.close();
						} catch (IOException e) {
							logger.error(e.getMessage(),e);
						}
					}));
				}
			}
		}
		return httpClient;
	}
	
	/**
	 * 功能描述: get请求
	 * @param proxyStr
	 * @param url
	 * @param headers
	 * @return String
	 * @version 2.0.0
	 * @author yuruige
	 */
	public static String get(String proxyStr,String url,Map<String, String> headers){
		HttpResponse response = null;
		try {
			HttpGet getRequest = new HttpGet(url);
			buildRequestHeader(getRequest,headers);
			if(proxyStr != null){
				buildRequestConfig(proxyStr, 5000, 2000, 2000, getRequest);
			}
			response = getHttpClient().execute(getRequest);
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
				return EntityUtils.toString(response.getEntity(), Charset.forName("utf-8"));
			}else{
				EntityUtils.consume(response.getEntity());
			}
		} catch (Exception e) {
			if(response != null){
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e1) {
					logger.error(e1.getMessage(),e1);
				}
			}
		} 
		return null;
	}
	
	/**
	 * 功能描述: Get请求
	 * @param proxyStr
	 * @param url
	 * @param headers
	 * @param socketTimeOut
	 * @param connectTimeout
	 * @param requestTimeOut
	 * @return String
	 * @version 2.0.0
	 * @author yuruige
	 */
	public static String get(String proxyStr,String url,Map<String, String> headers,int socketTimeOut,int connectTimeout,int requestTimeOut){
		HttpResponse response = null;
		try {
			HttpGet getRequest = new HttpGet(url);
			//设置请求头
			buildRequestHeader(getRequest,headers);
			if(proxyStr != null){
				//设置requestConfig
				buildRequestConfig(proxyStr, socketTimeOut, connectTimeout, requestTimeOut, getRequest);
			}
			response = getHttpClient().execute(getRequest);
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
				return EntityUtils.toString(response.getEntity(), Charset.forName("utf-8"));
			}else{
				EntityUtils.consume(response.getEntity());
			}
		} catch (Exception e) {
			if(response != null){
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e1) {
					logger.error(e1.getMessage(),e1);
				}
			}
		} 
		return null;
	}
	
	/**
	 * 功能描述: 
	 * @param request
	 * @param headers void
	 * @version 2.0.0
	 * @author yuruige
	 */
	 private static void buildRequestHeader(HttpRequestBase request, Map<String, String> headers) {
	        // 设置请求头
	        if (null != headers) {
	            for (Map.Entry<String, String> entry : headers.entrySet()) {
	                request.addHeader(entry.getKey(), entry.getValue());
	            }
	        }
	    }


	/**
	 * 功能描述: 重新设置requestConfig
	 * @param proxyStr
	 * @param socketTimeOut
	 * @param connectTimeout
	 * @param requestTimeOut
	 * @param request void
	 * @version 2.0.0
	 * @author yuruige
	 */
	private static void buildRequestConfig(String proxyStr, int socketTimeOut, int connectTimeout, int requestTimeOut,
			HttpRequestBase request) {
		request.setConfig(RequestConfig.custom().setSocketTimeout(socketTimeOut).setConnectTimeout(connectTimeout)
					.setConnectionRequestTimeout(requestTimeOut).setProxy(buildHttpHost(proxyStr)).build());
	}


	/**
	 * 功能描述: 构建代理用的httpHost
	 * @param proxyStr
	 * @return HttpHost
	 * @version 2.0.0
	 * @author yuruige
	 */
	private static HttpHost buildHttpHost(String proxyStr) {
		String[] temp = proxyStr.split(":");
		String proxyHostname = temp[0];
		int proxyPort = Integer.parseInt(temp[1]);
		// 依次是代理地址，代理端口号，协议类型
		return new HttpHost(proxyHostname, proxyPort, "http");
	}
	
	

}
