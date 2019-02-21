package androidovshchik.httpclass;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    CompositeDisposable disposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final HttpClient client = new HttpClient(getApplicationContext());
        disposable.add(Observable.fromCallable(new Callable<Object>() {

            @Override
            public Object call() {
                client.execute();
                return null;
            }
        }).subscribeOn(Schedulers.io())
            .subscribe());
    }

    @Override
    protected void onDestroy() {
        disposable.dispose();
        super.onDestroy();
    }
}
