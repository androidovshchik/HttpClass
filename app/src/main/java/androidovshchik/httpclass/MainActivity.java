package androidovshchik.httpclass;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.android.volley.toolbox.RequestFuture;

import java.util.concurrent.Callable;

import androidovshchik.http.HttpClient;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static com.android.volley.Request.Method.GET;
import static java.net.Proxy.Type.HTTP;

public class MainActivity extends AppCompatActivity {

    private final CompositeDisposable disposable = new CompositeDisposable();

    private HttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new HttpClient(getApplicationContext(), new HttpClient.Builder()
            .certificate(R.raw.debug)
            .userAgent(null)
            .timeout(30)
            .create());
        disposable.add(Observable.fromCallable(new Callable<String>() {

            @Override
            public String call() {
                String url = "https://cities.373soft.ru:8443/bridge-1.1/ws/driverServices/authorization?contractNumber=17491&password=0ef258774fcd5fb6013985f3662d1825&phone=+79194442212";
                HttpClient.MyProxy myProxy = new HttpClient.MyProxy()
                    .type(HTTP)
                    .host("45.32.152.77")
                    .port(28834)
                    .login("gzKdu0")
                    .password("ao13CD")
                    .create();
                RequestFuture<String> future = RequestFuture.newFuture();
                return client.execute(new MyStringRequest(GET, url, null, future), future);
            }
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<String>() {
                @Override
                public void accept(String response) {
                    ((TextView) findViewById(R.id.tv)).setText(response);
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    throwable.printStackTrace();
                    ((TextView) findViewById(R.id.tv)).setText(throwable.toString());
                }
            }));
    }

    @Override
    protected void onDestroy() {
        disposable.dispose();
        client.release();
        super.onDestroy();
    }
}
