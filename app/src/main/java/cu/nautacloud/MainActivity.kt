package cu.nautacloud

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import com.github.pwittchen.reactivenetwork.library.rx2.internet.observing.InternetObservingSettings
import com.google.android.material.snackbar.Snackbar
import cu.nautacloud.databinding.ActivityMainBinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers


class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var networkDisposable: Disposable? = null
    private var internetDisposable: Disposable? = null
    private val mp by lazy {
        MediaPlayer().apply {
            setDataSource(
                applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            setOnCompletionListener {
                it.start()
            }
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        }
    }
    private val wifiLock by lazy {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NautaDoWifiLock").apply {
            acquire()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.apply {
            setContentView(root)
            setSupportActionBar(toolbar)

            observeNetworkConnectivity()
            fab.setOnClickListener {
                if (internetDisposable == null || internetDisposable!!.isDisposed) {
                    observeInternetConnectivity()
                    fab.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@MainActivity,
                            R.drawable.cloud_search
                        )
                    )
                } else {
                    setAnimation("cloud_help")
                    internetDisposable?.dispose()
                    stopRingtone()
                    fab.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@MainActivity,
                            R.drawable.cloud_search_outline
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkDisposable?.dispose()
        internetDisposable?.dispose()
        mp.release()
        wifiLock.release()

    }

    private fun observeNetworkConnectivity() {
        var snackbar: Snackbar? = null
        networkDisposable = ReactiveNetwork
            .observeNetworkConnectivity(applicationContext)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { connectivity ->
                if (connectivity.typeName() == "WIFI") {
                    setAnimation("cloud_help")
                    snackbar?.dismiss()
                    binding.fab.visibility = View.VISIBLE
                } else {
                    setAnimation("cloud_error")
                    binding.fab.visibility = View.GONE
                    snackbar = Snackbar.make(
                        binding.root,
                        getString(R.string.connect_to_wifi_network),
                        Snackbar.LENGTH_INDEFINITE
                    )
                    snackbar?.show()
                    internetDisposable?.dispose()
                    stopRingtone()
                    binding.fab.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.cloud_search_outline
                        )
                    )
                }
            }
    }

    private fun observeInternetConnectivity() {
        internetDisposable = ReactiveNetwork
            .observeInternetConnectivity(
                InternetObservingSettings.builder()
                    .interval(30_000)
                    .timeout(10_000)
                    .build()
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isConnected ->
                if (isConnected) {
                    setAnimation("cloud_sync")
                    playRingtone()
                } else {
                    setAnimation("cloud_search")
                    stopRingtone()
                }
            }
    }

    private fun setAnimation(name: String) {
        binding.apply {
            animationView.setAnimation(resources.getIdentifier(name, "raw", packageName))
            animationView.playAnimation()
        }
    }

    private fun playRingtone() {
        mp.prepareAsync()
        mp.start()
    }

    private fun stopRingtone() {
        if (mp.isPlaying) mp.stop()
    }
}
