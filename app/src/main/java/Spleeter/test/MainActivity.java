package Spleeter.test;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.kbeanie.multipicker.api.AudioPicker;
import com.kbeanie.multipicker.api.Picker;
import com.kbeanie.multipicker.api.callbacks.AudioPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenAudio;
import com.opensooq.supernova.gligar.GligarPicker;

import Spleeter.test.databinding.ActivityMainBinding;
import Spleeter.test.ml.Stems;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import android.util.Log;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    ArrayList<Short> shorta = new ArrayList<>();

    private static final int PICKER_REQUEST_CODE = 101;
    private static final int PERMISSION_REQUEST_CODE = 102;
    AudioPicker audioPicker;
    Interpreter tflite;
    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        audioPicker = new AudioPicker(this);

        initListeners();
        checkPermission();


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    public void mp3ToFloat(String path) throws BitstreamException, DecoderException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(1024);
        final int sampleRate = 44100;
        final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                //  AudioFormat.ENCODING_PCM_FLOAT,
                minBufferSize,
                AudioTrack.MODE_STREAM);
        audioTrack.play();


        FileInputStream mp3Source = null;
        try {
            mp3Source = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "onActivityResult: Error " + e.getMessage());
        }
        Bitstream bitStream = new Bitstream(mp3Source);
        Decoder decoder = new Decoder();
        final int READ_THRESHOLD = 2147483647;

        Header frame;
        int framesReaded = 0;
        while (framesReaded++ <= READ_THRESHOLD && (frame = bitStream.readFrame()) != null) {
            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(frame, bitStream);
            short[] pcmChunk = sampleBuffer.getBuffer();
            for (int i = 0; i < pcmChunk.length; i++) {
                shorta.add(pcmChunk[i]);
            }

            for (short s : pcmChunk) {
                outStream.write(s & 0xff);
                outStream.write((s >> 8) & 0xff);
            }
            //   audioTrack.write(outStream.toByteArray(), 0, outStream.toByteArray().length);

            try {
                outStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "mp3ToFloat: Error");
            }
            bitStream.closeFrame();
        }


        ByteBuffer byteBuffer = ByteBuffer.wrap(outStream.toByteArray());

        try {
            Stems model = Stems.newInstance(this);

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 2}, DataType.FLOAT32);
            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Stems.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();

            // Releases model resources if no longer used.
            model.close();
            Log.e(TAG, "mp3ToFloat: Successful");
        } catch (IOException e) {
            // TODO Handle the
            Log.e(TAG, "mp3ToFloat: Error: " + e.getMessage());
        }

    }


    private void initListeners() {
        binding.btnOpenFile.setOnClickListener(v -> {
            selectAudioFile();
        });
    }

    public void selectAudioFile() {
        if (checkPermission()) {
            audioPicker.setAudioPickerCallback(new AudioPickerCallback() {
                @Override
                public void onAudiosChosen(List<ChosenAudio> files) {
                    // Convert Mp3 to float
                    try {
                        mp3ToFloat(files.get(0).getOriginalPath());
                    } catch (BitstreamException e) {
                        Log.e(TAG, "onActivityResult: Error " + e.getMessage());
                    } catch (DecoderException e) {
                        e.printStackTrace();
                        Log.e(TAG, "onActivityResult: Error " + e.getMessage());
                    }
                }

                @Override
                public void onError(String message) {
                    // Handle errors
                    Log.e(TAG, "onActivityResult: Error " + message);
                }
            });

            audioPicker.pickAudio();

        } else {

        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == Picker.PICK_AUDIO && resultCode == RESULT_OK) {
            audioPicker.submit(data);
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        // request permission if it has not been grunted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }
}