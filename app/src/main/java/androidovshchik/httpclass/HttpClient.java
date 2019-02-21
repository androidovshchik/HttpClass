package androidovshchik.httpclass;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
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
    private int retryCount;
    private int timeout;

    public HttpClient(Context context) {
        this(context, new Builder().create());
    }

    public HttpClient(Context context, Builder builder) {
        queue = Volley.newRequestQueue(context, new ProxyStack());
        certificate = builder.certificate;
        retryCount = builder.retryCount;
        timeout = builder.timeout;
    }

    public String getSync(String url) {
        return getSync(url, null);
    }

    public String getSync(String url, final Map<String, String> headers) {
        RequestFuture<String> future = RequestFuture.newFuture();
        return execute(future, new StringRequest(Request.Method.GET, url, future, future) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers != null ? headers : super.getHeaders();
            }
        });
    }

    public String postSync(String url) {
        return postSync(url, null, null, null);
    }

    public String postSync(String url, final String contentType, final byte[] body) {
        return postSync(url, contentType, body, null);
    }

    public String postSync(String url, final String contentType, final byte[] body, final Map<String, String> headers) {
        RequestFuture<String> future = RequestFuture.newFuture();
        return execute(future, new StringRequest(Request.Method.POST, url, future, future) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers != null ? headers : super.getHeaders();
            }

            @Override
            public String getBodyContentType() {
                return contentType != null ? contentType : super.getBodyContentType();
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return body != null ? body : super.getBody();
            }
        });
    }

    protected String execute(RequestFuture<String> future, StringRequest request) {
        request.setRetryPolicy(new DefaultRetryPolicy(timeout, retryCount,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            VolleyLog.e(e, null);
        } catch (ExecutionException e) {
            VolleyLog.e(e, null);
        } catch (TimeoutException e) {
            VolleyLog.e(e, null);
        }
        return null;
    }

    public static class Builder {

        private int certificate = 0;
        private int retryCount = DefaultRetryPolicy.DEFAULT_MAX_RETRIES;
        private int timeout = DefaultRetryPolicy.DEFAULT_TIMEOUT_MS;

        public Builder certificate(int rawId) {
            certificate = rawId;
            return this;
        }

        public Builder retryCount(int count) {
            retryCount = count;
            return this;
        }

        public Builder timeout(int milliseconds) {
            timeout = milliseconds;
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

    public static class CustomRequest<T> extends Request<T> {

        public CustomRequest(int method, String url, @Nullable Response.ErrorListener listener) {
            super(method, url, listener);
        }

        @Override
        protected Response<T> parseNetworkResponse(NetworkResponse response) {
            return null;
        }

        @Override
        protected void deliverResponse(T response) {

        }
    }

    public static class ProxyStack extends HurlStack {

        private static final int HTTP_CONTINUE = 100;

        /**
         * An interface for transforming URLs before use.
         */
        public interface UrlRewriter {
            /**
             * Returns a URL to use instead of the provided one, or null to indicate this URL should not
             * be used at all.
             */
            String rewriteUrl(String originalUrl);
        }

        private final HurlStack.UrlRewriter mUrlRewriter;
        private final SSLSocketFactory mSslSocketFactory;

        public ProxyStack() {
            this(/* urlRewriter = */ null);
        }

        /**
         * @param urlRewriter Rewriter to use for request URLs
         */
        public ProxyStack(HurlStack.UrlRewriter urlRewriter) {
            this(urlRewriter, /* sslSocketFactory = */ null);
        }

        /**
         * @param urlRewriter      Rewriter to use for request URLs
         * @param sslSocketFactory SSL factory to use for HTTPS connections
         */
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
                    new HurlStack.UrlConnectionInputStream(connection));
            } finally {
                if (!keepConnectionOpen) {
                    connection.disconnect();
                }
            }
        }

        @VisibleForTesting
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

        /**
         * Checks if a response message contains a body.
         *
         * @param requestMethod request method
         * @param responseCode  response status code
         * @return whether the response has a body
         * @see <a href="https://tools.ietf.org/html/rfc7230#section-3.3">RFC 7230 section 3.3</a>
         */
        private static boolean hasResponseBody(int requestMethod, int responseCode) {
            return requestMethod != Request.Method.HEAD
                && !(HTTP_CONTINUE <= responseCode && responseCode < HttpURLConnection.HTTP_OK)
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT
                && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED;
        }

        /**
         * Wrapper for a {@link HttpURLConnection}'s InputStream which disconnects the connection on
         * stream close.
         */
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

        /**
         * Initializes an {@link InputStream} from the given {@link HttpURLConnection}.
         *
         * @param connection
         * @return an HttpEntity populated with data from <code>connection</code>.
         */
        private static InputStream inputStreamFromConnection(HttpURLConnection connection) {
            InputStream inputStream;
            try {
                inputStream = connection.getInputStream();
            } catch (IOException ioe) {
                inputStream = connection.getErrorStream();
            }
            return inputStream;
        }

        /**
         * Create an {@link HttpURLConnection} for the specified {@code url}.
         */
        protected HttpURLConnection createConnection(URL url) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Workaround for the M release HttpURLConnection not observing the
            // HttpURLConnection.setFollowRedirects() property.
            // https://code.google.com/p/android/issues/detail?id=194495
            connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());

            return connection;
        }

        /**
         * Opens an {@link HttpURLConnection} with parameters.
         *
         * @param url
         * @return an open connection
         * @throws IOException
         */
        private HttpURLConnection openConnection(URL url, Request<?> request) throws IOException {
            HttpURLConnection connection = createConnection(url);

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
            if (!connection.getRequestProperties().containsKey(HttpHeaderParser.HEADER_CONTENT_TYPE)) {
                connection.setRequestProperty(
                    HttpHeaderParser.HEADER_CONTENT_TYPE, request.getBodyContentType());
            }
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.write(body);
            out.close();
        }
    }
}