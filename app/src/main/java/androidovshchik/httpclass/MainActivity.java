package androidovshchik.httpclass;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.RequestFuture;

import java.net.Proxy;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static com.android.volley.Request.Method.GET;

public class MainActivity extends AppCompatActivity {

    private final CompositeDisposable disposable = new CompositeDisposable();

    private HttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new HttpClient(getApplicationContext());
        disposable.add(Observable.fromCallable(new Callable<String>() {

            @Override
            public String call() {
                String url = "https://telegram.org";
                HttpClient.MyProxy myProxy = new HttpClient.MyProxy()
                    .type(Proxy.Type.HTTP)
                    .host("")
                    .port(0)
                    .login("")
                    .password("")
                    .create();
                RequestFuture<String> future = RequestFuture.newFuture();
                return client.execute(new HttpClient.MyStringRequest(GET, url, myProxy, future), future);
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
                    Toast.makeText(getApplicationContext(), throwable.toString(), Toast.LENGTH_SHORT)
                        .show();
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
