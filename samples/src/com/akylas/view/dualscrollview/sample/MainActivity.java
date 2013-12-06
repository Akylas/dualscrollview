/*
 * Copyright (C) 2013 Akylas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.akylas.view.dualscrollview.sample;

import java.util.ArrayList;

import com.akylas.view.DualScrollView;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String LOG_TAG = MainActivity.class.getSimpleName();

	private LinearLayout mTwoDContent;
	private DualScrollView mTwoDScrollView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTwoDContent = (LinearLayout) findViewById(R.id.twoDContent);
		mTwoDScrollView = (DualScrollView) findViewById(R.id.dualScrollView);

		
		for (int i=0; i<30; i++) {
			LinearLayout layout2 = new LinearLayout(this);
			layout2.setOrientation(LinearLayout.VERTICAL);
			for (int j=0; j<30; j++) {
				TextView view = new TextView(this);
				view.setPadding(10, 10, 10, 10);
				view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				view.setText("data" + i + "/"  + j);
				layout2.addView(view);
			}
			mTwoDContent.addView(layout2);
		}
	}
}