package com.specknet.pdiotapp.utils;

import com.specknet.pdiotapp.ml.TestModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;

public class ModelClassification {


//    public static void classifyActivity() {
//        try {
//            TestModel model = TestModel.newInstance(getApplicationContext());
//
//            // Creates inputs for reference.
//            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 75, 6}, DataType.FLOAT32);
//            inputFeature0.loadBuffer(byteBuffer);
//
//            // Runs model inference and gets result.
//            TestModel.Outputs outputs = model.process(inputFeature0);
//            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//
//            // Releases model resources if no longer used.
//            model.close();
//        } catch (IOException e) {
//            // TODO Handle the exception
//        }
//    }


}
