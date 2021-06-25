// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codelab.mlkit;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.codelab.mlkit.GraphicOverlay.Graphic;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    private ImageView mImageView;
    private ImageView mRefImageView;
    private TextView mPingTextView;
    private Button mTextButton;
    private Button mFaceButton;
    private Bitmap mSelectedImage;
    private GraphicOverlay mGraphicOverlay;
    // Max width (portrait mode)
    private Integer mImageMaxWidth;
    // Max height (portrait mode)
    private Integer mImageMaxHeight;

    private Integer mMaskSpec[][];
    private Integer mSelectedGame;
    private MediaMetadataRetriever mRetriever;
    private int mCurFrame = 1;
    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
                                o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.image_view);
        mRefImageView = findViewById(R.id.image_view_ref);

        mPingTextView = findViewById(R.id.text_view_ping);

        mTextButton = findViewById(R.id.button_text);
        mFaceButton = findViewById(R.id.button_face);


        mRetriever = new MediaMetadataRetriever();

        mCurFrame = 1;

        mGraphicOverlay = findViewById(R.id.graphic_overlay);
        mTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTextRecognition();
            }
        });
        mFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runVideoProcessing();
            }
        });
        Spinner dropdown = findViewById(R.id.spinner);
        String[] items = new String[]{"PubG", "WildRift","WildRift 720p"};
        mMaskSpec = new Integer[3][4];
        mMaskSpec[0] = new Integer[]{80, 1032, 72, 48};
        mMaskSpec[1] = new Integer[]{1540, 140, 72, 48};
        mMaskSpec[2] = new Integer[]{1026, 89, 48, 35};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout
                .simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        dropdown.setOnItemSelectedListener(this);
    }

    private Bitmap scaleAndSetBitmap(Bitmap b, ImageView im){
        Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

        int targetWidth = targetedSize.first;
        int maxHeight = targetedSize.second;

        // Determine how much to scale down the image
        float scaleFactor =
                Math.max(
                        (float) b.getWidth() / (float) targetWidth,
                        (float) b.getHeight() / (float) maxHeight);

        Bitmap resizedBitmap =
                Bitmap.createScaledBitmap(
                        b,
                        (int) (b.getWidth() / scaleFactor),
                        (int) (b.getHeight() / scaleFactor),
                        true);
        im.setImageBitmap(resizedBitmap);
        return resizedBitmap;
    }


    private void runVideoProcessing(){

        String path = (String) Environment.getExternalStorageDirectory().getAbsolutePath();
        mRetriever.setDataSource(path+"/Download/wr.mp4");
        mCurFrame = 1;
        final Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Bitmap f = mRetriever.getFrameAtTime(1000000 * mCurFrame,MediaMetadataRetriever.OPTION_NEXT_SYNC);
                scaleAndSetBitmap(f, mRefImageView);

                Bitmap maskBitmap = Bitmap.createBitmap(f, mMaskSpec[mSelectedGame][0],
                        mMaskSpec[mSelectedGame][1], mMaskSpec[mSelectedGame][2], mMaskSpec[mSelectedGame][3]);
                Bitmap rf = scaleAndSetBitmap(maskBitmap,mImageView);

                mSelectedImage = rf;
                runTextRecognition();
                mCurFrame ++;
                mHandler.postDelayed(this, 10);
            }
        }, 10);
    }



    private void runTextRecognition() {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        TextRecognizer recognizer = TextRecognition.getClient();
        mTextButton.setEnabled(false);
        recognizer.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text texts) {
                                mTextButton.setEnabled(true);
                                processTextRecognitionResult(texts);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                mTextButton.setEnabled(true);
                                e.printStackTrace();
                            }
                        });
    }

    private void processTextRecognitionResult(Text texts) {
        List<Text.TextBlock> blocks = texts.getTextBlocks();
        for ( int i = 0; i < blocks.size();i++){

            // blocks.get(i).getText().indexOf("m5") > 0

            if(blocks.get(i).getText().indexOf("ms") > 0 ){
                //Toast.makeText(getApplicationContext(),blocks.get(i).getText(),Toast.LENGTH_SHORT).show();
                try{
                    Log.e("madmachine", blocks.get(i).getText().substring(0,blocks.get(i).getText().length()-2));
                } catch(Exception e){
                    Log.e("madmachine", "caught exc: " + blocks.get(i).getText());
                }
                mPingTextView.setText("Detected Ping: "+ blocks.get(i).getText());
            }
        }

        if (blocks.size() == 0) {
            //showToast("No text found");
            return;
        }
//        mGraphicOverlay.clear();
//        for (int i = 0; i < blocks.size(); i++) {
//            List<Text.Line> lines = blocks.get(i).getLines();
//            for (int j = 0; j < lines.size(); j++) {
//                List<Text.Element> elements = lines.get(j).getElements();
//                for (int k = 0; k < elements.size(); k++) {
//                    Graphic textGraphic = new TextGraphic(mGraphicOverlay, elements.get(k));
//                    mGraphicOverlay.add(textGraphic);
//
//                }
//            }
//        }
    }

    private void runFaceContourDetection() {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();

        mFaceButton.setEnabled(false);
        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                mFaceButton.setEnabled(true);
                                processFaceContourDetectionResult(faces);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                mFaceButton.setEnabled(true);
                                e.printStackTrace();
                            }
                        });

    }

    private void processFaceContourDetectionResult(List<Face> faces) {
        // Task completed successfully
        if (faces.size() == 0) {
            showToast("No face found");
            return;
        }
        mGraphicOverlay.clear();
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.get(i);
            FaceContourGraphic faceGraphic = new FaceContourGraphic(mGraphicOverlay);
            mGraphicOverlay.add(faceGraphic);
            faceGraphic.updateFace(face);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxWidth() {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mImageView.getWidth();
        }

        return mImageMaxWidth;
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxHeight() {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight =
                    mImageView.getHeight();
        }

        return mImageMaxHeight;
    }

    // Gets the targeted width / height.
    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;
        int maxWidthForPortraitMode = getImageMaxWidth();
        int maxHeightForPortraitMode = getImageMaxHeight();
        targetWidth = maxWidthForPortraitMode;
        targetHeight = maxHeightForPortraitMode;
        return new Pair<>(targetWidth, targetHeight);
    }

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        mGraphicOverlay.clear();
        switch (position) {
            case 0:
                //mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg");
                mSelectedImage = getBitmapFromAsset(this, "pubg_hd.png");

                break;
//            case 1:
//                // Whatever you want to happen when the thrid item gets selected
//                mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg");
//                break;
            case 1:
                mSelectedImage = getBitmapFromAsset(this, "snap_hd.png");
                break;
            case 2:
                //mSelectedImage = getBitmapFromAsset(this, "snap_hd.png");
                mSelectedImage = getBitmapFromAsset(this, "snap_sd.png");
                break;

        }
        mSelectedGame = position;
        mPingTextView.setText("-");

        if (mSelectedImage != null) {
            scaleAndSetBitmap(mSelectedImage, mRefImageView);

            Bitmap maskBitmap = Bitmap.createBitmap(mSelectedImage, mMaskSpec[position][0],
                    mMaskSpec[position][1], mMaskSpec[position][2], mMaskSpec[position][3]);
            Bitmap resizedBitmapv2 = scaleAndSetBitmap(maskBitmap,mImageView);
            mSelectedImage = resizedBitmapv2;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream is;
        Bitmap bitmap = null;
        try {
            is = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }
}
