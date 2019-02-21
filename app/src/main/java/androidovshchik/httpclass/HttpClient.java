package androidovshchik.httpclass;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    public static class ProxyStack extends HurlStack {

    }
}