package androidovshchik.httpclass;

import android.content.Context;
import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;

import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("unused")
public class HttpClient {

    static {
        VolleyLog.DEBUG = BuildConfig.DEBUG;
    }

    protected final RequestQueue queue;

    private int certificate;
    private int timeout;

    public HttpClient(Context context) {
        this(context, new Builder().create());
    }

    public HttpClient(Context context, Builder builder) {
        queue = Volley.newRequestQueue(context, new ProxyStack());
        certificate = builder.certificate;
        timeout = builder.timeout;
    }

    public <T> T execute(Request<T> myRequest, RequestFuture<T> future) {
        queue.add(myRequest);
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            VolleyLog.e(e, "");
        } catch (ExecutionException e) {
            VolleyLog.e(e, "");
        } catch (TimeoutException e) {
            VolleyLog.e(e, "");
        }
        return null;
    }

    public void release() {
        queue.stop();
    }

    public static class Builder {

        private int certificate = 0;
        private int timeout = 30;

        public Builder certificate(int rawId) {
            certificate = rawId;
            return this;
        }

        public Builder timeout(int seconds) {
            timeout = seconds;
            return this;
        }

        public Builder create() {
            return this;
        }
    }

    public static class MyProxy {

        private Proxy.Type proxyType = Proxy.Type.HTTP;
        private String proxyHost = "http://0";
        private int proxyPort = 80;
        private String proxyLogin = "qwerty";
        private String proxyPassword = "12345";

        public MyProxy type(Proxy.Type type) {
            proxyType = type;
            return this;
        }

        public MyProxy host(String host) {
            proxyHost = host;
            return this;
        }

        public MyProxy port(int port) {
            proxyPort = port;
            return this;
        }

        public MyProxy login(String login) {
            proxyLogin = login;
            return this;
        }

        public MyProxy password(String password) {
            proxyPassword = password;
            return this;
        }

        public MyProxy create() {
            return this;
        }

        public Map<String, String> headers() throws UnsupportedEncodingException {
            String credentials = Base64.encodeToString((proxyLogin + proxyPassword)
                .getBytes("UTF-8"), Base64.NO_WRAP);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authenticate", "Basic " + credentials);
            headers.put("Authorization", "Basic " + credentials);
            headers.put("Proxy-Authorization", "Basic " + credentials);
            headers.put("Proxy-Authenticate", "Basic " + credentials);
            headers.put("WWW-Authorization", "Basic " + credentials);
            headers.put("WWW-Authenticate", "Basic " + credentials);
            return headers;
        }

        @Override
        public String toString() {
            return "MyProxy{" +
                "proxyHost='" + proxyHost + '\'' +
                ", proxyPort=" + proxyPort +
                ", proxyLogin='" + proxyLogin + '\'' +
                ", proxyPassword='" + proxyPassword + '\'' +
                ", proxyType=" + proxyType +
                '}';
        }
    }

    public static abstract class MyRequest<T> extends Request<T> {

        protected MyProxy myProxy;

        public MyRequest(int method, String url, MyProxy myProxy, RequestFuture<String> future) {
            super(method, url, future);
            this.myProxy = myProxy;
            setShouldCache(false);
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> headers = new HashMap<>(super.getHeaders());
            if (myProxy != null) {
                try {
                    headers.putAll(myProxy.headers());
                } catch (UnsupportedEncodingException e) {
                    VolleyLog.e(e, "");
                }
            }
            VolleyLog.d("%s", headers.toString());
            return headers;
        }
    }

    /**
     * Based on HurlStack class
     * @see HurlStack
     */
    private static class ProxyStack extends BaseHttpStack {

        private static final String HEADER_CONTENT_TYPE = "Content-Type";

        private static final int HTTP_CONTINUE = 100;

        private final HurlStack.UrlRewriter mUrlRewriter;
        private final SSLSocketFactory mSslSocketFactory;

        public ProxyStack() {
            this(null);
        }

        public ProxyStack(HurlStack.UrlRewriter urlRewriter) {
            this(urlRewriter, null);
        }

        public ProxyStack(HurlStack.UrlRewriter urlRewriter, SSLSocketFactory sslSocketFactory) {
            mUrlRewriter = urlRewriter;
            mSslSocketFactory = sslSocketFactory;
        }

        @Override
        public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
            String url = request.getUrl();
            HashMap<String, String> map = new HashMap<>();
            map.putAll(additionalHeaders);
            // Request.getHeaders() takes precedence over the given additional (cache) headers).
            map.putAll(request.getHeaders());
            if (mUrlRewriter != null) {
                String rewritten = mUrlRewriter.rewriteUrl(url);
                if (rewritten == null) {
                    throw new IOException("URL blocked by rewriter: " + url);
                }
                url = rewritten;
            }
            URL parsedUrl = new URL(url);
            HttpURLConnection connection = openConnection(parsedUrl, request);
            boolean keepConnectionOpen = false;
            try {
                for (String headerName : map.keySet()) {
                    connection.setRequestProperty(headerName, map.get(headerName));
                }
                setConnectionParametersForRequest(connection, request);
                // Initialize HttpResponse with data from the HttpURLConnection.
                int responseCode = connection.getResponseCode();
                if (responseCode == -1) {
                    // -1 is returned by getResponseCode() if the response code could not be retrieved.
                    // Signal to the caller that something was wrong with the connection.
                    throw new IOException("Could not retrieve response code from HttpUrlConnection.");
                }

                if (!hasResponseBody(request.getMethod(), responseCode)) {
                    return new HttpResponse(responseCode, convertHeaders(connection.getHeaderFields()));
                }

                // Need to keep the connection open until the stream is consumed by the caller. Wrap the
                // stream such that close() will disconnect the connection.
                keepConnectionOpen = true;
                return new HttpResponse(
                    responseCode,
                    convertHeaders(connection.getHeaderFields()),
                    connection.getContentLength(),
                    new ProxyStack.UrlConnectionInputStream(connection));
            } finally {
                if (!keepConnectionOpen) {
                    connection.disconnect();
                }
            }
        }

        static List<Header> convertHeaders(Map<String, List<String>> responseHeaders) {
            List<Header> headerList = new ArrayList<>(responseHeaders.size());
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                // HttpUrlConnection includes the status line as a header with a null key; omit it here
                // since it's not really a header and the rest of Volley assumes non-null keys.
                if (entry.getKey() != null) {
                    for (String value : entry.getValue()) {
                        headerList.add(new Header(entry.getKey(), value));
                    }
                }
            }
            return headerList;
        }

        private static boolean hasResponseBody(int requestMethod, int responseCode) {
            return requestMethod != Request.Method.HEAD
                && !(HTTP_CONTINUE <= responseCode && responseCode < HttpURLConnection.HTTP_OK)
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT
                && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED;
        }

        static class UrlConnectionInputStream extends FilterInputStream {
            private final HttpURLConnection mConnection;

            UrlConnectionInputStream(HttpURLConnection connection) {
                super(inputStreamFromConnection(connection));
                mConnection = connection;
            }

            @Override
            public void close() throws IOException {
                super.close();
                mConnection.disconnect();
            }
        }

        private static InputStream inputStreamFromConnection(HttpURLConnection connection) {
            InputStream inputStream;
            try {
                inputStream = connection.getInputStream();
            } catch (IOException ioe) {
                inputStream = connection.getErrorStream();
            }
            return inputStream;
        }

        protected HttpURLConnection createConnection(URL url, Request<?> request) throws IOException {
            if (!(request instanceof MyRequest)) {
                throw new IOException("Request must be instance of MyRequest class");
            }
            MyProxy myProxy = ((MyRequest) request).myProxy;
            VolleyLog.d("%s", myProxy.toString());
            SocketAddress address = InetSocketAddress.createUnresolved(myProxy.proxyHost, myProxy.proxyPort);
            Proxy proxy = new Proxy(myProxy.proxyType, address);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);

            // Workaround for the M release HttpURLConnection not observing the
            // HttpURLConnection.setFollowRedirects() property.
            // https://code.google.com/p/android/issues/detail?id=194495
            connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());

            return connection;
        }

        private HttpURLConnection openConnection(URL url, Request<?> request) throws IOException {
            HttpURLConnection connection = createConnection(url, request);

            int timeoutMs = request.getTimeoutMs();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setDoInput(true);

            // use caller-provided custom SslSocketFactory, if any, for HTTPS
            if ("https".equals(url.getProtocol()) && mSslSocketFactory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(mSslSocketFactory);
            }

            return connection;
        }

        // NOTE: Any request headers added here (via setRequestProperty or addRequestProperty) should be
        // checked against the existing properties in the connection and not overridden if already set.
        @SuppressWarnings("deprecation")
        /* package */ static void setConnectionParametersForRequest(
            HttpURLConnection connection, Request<?> request) throws IOException, AuthFailureError {
            switch (request.getMethod()) {
                case Request.Method.DEPRECATED_GET_OR_POST:
                    // This is the deprecated way that needs to be handled for backwards compatibility.
                    // If the request's post body is null, then the assumption is that the request is
                    // GET.  Otherwise, it is assumed that the request is a POST.
                    byte[] postBody = request.getPostBody();
                    if (postBody != null) {
                        connection.setRequestMethod("POST");
                        addBody(connection, request, postBody);
                    }
                    break;
                case Request.Method.GET:
                    // Not necessary to set the request method because connection defaults to GET but
                    // being explicit here.
                    connection.setRequestMethod("GET");
                    break;
                case Request.Method.DELETE:
                    connection.setRequestMethod("DELETE");
                    break;
                case Request.Method.POST:
                    connection.setRequestMethod("POST");
                    addBodyIfExists(connection, request);
                    break;
                case Request.Method.PUT:
                    connection.setRequestMethod("PUT");
                    addBodyIfExists(connection, request);
                    break;
                case Request.Method.HEAD:
                    connection.setRequestMethod("HEAD");
                    break;
                case Request.Method.OPTIONS:
                    connection.setRequestMethod("OPTIONS");
                    break;
                case Request.Method.TRACE:
                    connection.setRequestMethod("TRACE");
                    break;
                case Request.Method.PATCH:
                    connection.setRequestMethod("PATCH");
                    addBodyIfExists(connection, request);
                    break;
                default:
                    throw new IllegalStateException("Unknown method type.");
            }
        }

        private static void addBodyIfExists(HttpURLConnection connection, Request<?> request)
            throws IOException, AuthFailureError {
            byte[] body = request.getBody();
            if (body != null) {
                addBody(connection, request, body);
            }
        }

        private static void addBody(HttpURLConnection connection, Request<?> request, byte[] body)
            throws IOException {
            // Prepare output. There is no need to set Content-Length explicitly,
            // since this is handled by HttpURLConnection using the size of the prepared
            // output stream.
            connection.setDoOutput(true);
            // Set the content-type unless it was already set (by Request#getHeaders).
            if (!connection.getRequestProperties().containsKey(HEADER_CONTENT_TYPE)) {
                connection.setRequestProperty(HEADER_CONTENT_TYPE, request.getBodyContentType());
            }
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.write(body);
            out.close();
        }
    }
}