package androidovshchik.httpclass;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpResponse;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unused", "NullableProblems", "ConstantConditions"})
public class HttpClient implements Response.Listener<Object>, Response.ErrorListener {

    protected final RequestQueue queue;

    protected MyProxy myProxy;

    protected HurlStack hurlStack = new HurlStack() {

        @Override
        protected HttpURLConnection createConnection(URL url) throws IOException {
            url.
                Proxy proxy = new Proxy(myProxy.proxyType, InetSocketAddress.createUnresolved(myProxy.proxyHost, myProxy.proxyPort));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            // Workaround for the M release HttpURLConnection not observing the
            // HttpURLConnection.setFollowRedirects() property.
            // https://code.google.com/p/android/issues/detail?id=194495
            connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());
            return connection;
        }

        @Override
        public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
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
                    new UrlConnectionInputStream(connection));
            } finally {
                if (!keepConnectionOpen) {
                    connection.disconnect();
                }
            }
        }
    };

    public HttpClient(Context context) {
        this(context, new Builder().create());
    }

    public HttpClient(Context context, Builder builder) {
        queue = Volley.newRequestQueue(context, hurlStack);
    }

    public getJson() {
        new JsonObjectRequest(Request.Method.GET, "", null, this, this);
    }

    /*public Response get(String url) throws IOException {
        return get(url, null);
    }

    public Response get(String url, Headers headers) throws IOException {
        return client.newCall(new Request.Builder()
            .url(url)
            .headers(headers != null ? headers : new Headers.Builder().build())
            .tag("")
            .build())
            .execute();
    }

    public Response post(String url, RequestBody body) throws IOException {
        return post(url, body, null);
    }

    public Response post(String url, RequestBody body, Headers headers) throws IOException {
        return client.newCall(new Request.Builder()
            .url(url)
            .headers(headers != null ? headers : new Headers.Builder().build())
            .post(body)
            .tag("")
            .build())
            .execute();
    }

    public void getAsync(String url) {
        getAsync(url, null);
    }

    public void getAsync(String url, Headers headers) {
        client.newCall(new Request.Builder()
            .url(url)
            .headers(headers != null ? headers : new Headers.Builder().build())
            .tag("")
            .build())
            .enqueue(this);
    }

    public void postAsync(String url, RequestBody body) {
        postAsync(url, body, null);
    }

    public void postAsync(String url, RequestBody body, Headers headers) {
        client.newCall(new Request.Builder()
            .url(url)
            .headers(headers != null ? headers : new Headers.Builder().build())
            .post(body)
            .tag("")
            .build())
            .enqueue(this);
    }*/

    public void cancel(String tag) {

    }

    public void cancelAll() {

    }

    @Override
    public void onResponse(Object response) {

    }

    @Override
    public void onErrorResponse(VolleyError error) {
        error.printStackTrace();
    }

    public static class Builder {

        private int connectTimeout = 15;
        private int writeTimeout = 0;
        private int readTimeout = 0;
        private int certificate = 0;

        public Builder connectTimeout(int seconds) {
            connectTimeout = seconds;
            return this;
        }

        public Builder writeTimeout(int seconds) {
            writeTimeout = seconds;
            return this;
        }

        public Builder readTimeout(int seconds) {
            readTimeout = seconds;
            return this;
        }

        public Builder certificate(int rawId) {
            certificate = rawId;
            return this;
        }

        public Builder create() {
            return this;
        }
    }

    public static class MyProxy {

        private String proxyHost = "";
        private int proxyPort = 80;
        private String proxyLogin = "";
        private String proxyPassword = "";
        private Proxy.Type proxyType = Proxy.Type.HTTP;

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

        public MyProxy type(Proxy.Type type) {
            proxyType = type;
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
}