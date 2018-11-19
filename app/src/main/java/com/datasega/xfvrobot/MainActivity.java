package com.datasega.xfvrobot;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;
import org.json.JSONTokener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.speech.setting.TtsSettings;

import android.Manifest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.content.SharedPreferences;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.speech.util.FucUtil;
import com.iflytek.speech.util.JsonParser;
import com.iflytek.sunflower.FlowerCollector;
public class MainActivity extends Activity implements OnClickListener{
	private final String TAG = "MainActivity";
	private Toast mToast;
	private String chatbotUrl ="";
	// 语音合成对象
		
		private SpeechSynthesizer mTts;
		// 默认发音人
		private String voicer = "xiaoyan";
		
		private String[] mCloudVoicersEntries;
		private String[] mCloudVoicersValue ;
		
		// 缓冲进度
		private int mPercentForBuffering = 0;
		// 播放进度
		private int mPercentForPlaying = 0;
		
		// 引擎类型
		private String mEngineType = SpeechConstant.TYPE_CLOUD;

		private SharedPreferences mSharedPreferences;
		
		
		// 语音识别对象
		private SpeechRecognizer mAsr;
		// 云端语法文件
		private String mCloudGrammar = null;
			
		private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
		private static final String GRAMMAR_TYPE_ABNF = "abnf";
		private static final String GRAMMAR_TYPE_BNF = "bnf";
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		//应用每次需要危险权限时，都要判断应用目前是否有该权限。兼容库中已经做了封装，只需要通过下面代码即可
		int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR);
		ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CODE);
        setContentView(R.layout.activity_main);
    	findViewById(R.id.button_start).setOnClickListener(MainActivity.this);
    	findViewById(R.id.button_stop).setOnClickListener(MainActivity.this);
    	findViewById(R.id.button_testtts).setOnClickListener(MainActivity.this);
    	
        mToast = Toast.makeText(this,"",Toast.LENGTH_SHORT);	
        mSharedPreferences = getSharedPreferences(TtsSettings.PREFER_NAME, MODE_PRIVATE);
        initTTS();
        initReco();
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    private void initTTS(){
    	
    	// 初始化合成对象
		mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, mTtsInitListener);
		
		// 云端发音人名称列表
		mCloudVoicersEntries = getResources().getStringArray(R.array.voicer_cloud_entries);
		mCloudVoicersValue = getResources().getStringArray(R.array.voicer_cloud_values);
				
		
    }
	/**
	 * 初始化监听。
	 */
	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	} else {
				// 初始化成功，之后可以调用startSpeaking方法
        		// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
        		// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}		
		}
	};

	/**
	 * 合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {
		
		@Override
		public void onSpeakBegin() {
			showTip("开始播放");
		}

		@Override
		public void onSpeakPaused() {
			showTip("暂停播放");
		}

		@Override
		public void onSpeakResumed() {
			showTip("继续播放");
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos,
				String info) {
			// 合成进度
			mPercentForBuffering = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			// 播放进度
			mPercentForPlaying = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
		}

		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				showTip("播放完成");
			} else if (error != null) {
				showTip(error.getPlainDescription(true));
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
    private void initReco(){
    	// 初始化识别对象
		mAsr = SpeechRecognizer.createRecognizer(MainActivity.this, mRecoInitListener);		
		mCloudGrammar = FucUtil.readFile(this,"grammar_sample.abnf","utf-8");
		
    }
    /**
     * 初始化监听器。
     */
    private InitListener mRecoInitListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	}
		}
    };
    private RecognizerListener mRecognizerListener = new RecognizerListener() {
        
        @Override
        public void onVolumeChanged(int volume, byte[] data) {
        	showTip("当前正在说话，音量大小：" + volume);
        	Log.d(TAG, "返回音频数据："+data.length);
        }
        	private String mRecoBuffer = "";
        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
        	if (null != result) {
        		Log.d(TAG, "recognizer result：" + result.getResultString());
        		String text ;
        		if("cloud".equalsIgnoreCase(mEngineType)){
        			text = JsonParser.parseGrammarResult(result.getResultString());
        		}else {
        			text = JsonParser.parseLocalGrammarResult(result.getResultString());
        		}
        		if(isLast){
        			text = mRecoBuffer;
        		}
        		else {
        			text = JsonParser.parseResult(result.getResultString());
        			mRecoBuffer +=text;
        			if (mRecoBuffer != null && (!"".equals(mRecoBuffer))) {
    					new AsyncTask<String, Void, String>() {
    						@Override
    						protected String doInBackground(String... params) {

    							String tmpinput = params[0];
    							String tmppinyin = "";
    							try {
    								Cn2Spell tmpspell = new Cn2Spell();
    								tmppinyin = tmpspell.getSelling(tmpinput);
    							} catch (Exception ex1) {
    							}
    							if (tmpinput.startsWith("小凡请告诉我")
    									|| tmppinyin
    											.startsWith("xiaofanqinggaosuwo")) {
    								tmpinput = tmpinput
    										.substring("小凡请告诉我".length());
    							} else if (tmpinput.startsWith("请小凡说下")
    									|| tmppinyin
    											.startsWith("qingxiaofanshuoxia")
    									|| tmpinput.startsWith("小凡请教下")
    									|| tmppinyin
    											.startsWith("xiaofanqingjiaoxia")) {
    								tmpinput = tmpinput.substring("请小凡说下".length());
    							} else if (tmpinput.startsWith("请问小凡")
    									|| tmpinput.startsWith("请问小梵")
    									|| tmpinput.startsWith("请教小凡")
    									|| tmppinyin.startsWith("qingjiaoxiaofan")
    									|| tmppinyin.startsWith("qingwenxiaofan")
    									|| tmppinyin.startsWith("xiaofanqingwen")) {
    								tmpinput = tmpinput.substring("请问小凡".length());
    							} else if (tmpinput.startsWith("小凡")
    									|| tmpinput.startsWith("小梵")
    									|| tmppinyin.startsWith("xiaofan")) {
    								tmpinput = tmpinput.substring("小凡".length());
    							}
    							if (tmpinput.endsWith("。"))
    								tmpinput = tmpinput.substring(0,
    										tmpinput.length() - "。".length());

    							if (!"".equals(tmpinput)) {

    								InputStream inputStream = null;
    								HttpURLConnection urlConnection = null;
    								String tmpanswer = "小凡暂时不能提供服务，请稍后重试";
    								try {
    									// {"code":0,"message":"","answer":"欢迎使用小凡说股"}
    									URL url = new URL(
    											chatbotUrl);
    									urlConnection = (HttpURLConnection) url
    											.openConnection();

    									/* optional request header */
    									urlConnection.setRequestProperty(
    											"Content-Type",
    											"application/json; charset=UTF-8");

    									/* optional request header */
    									urlConnection.setRequestProperty("Accept",
    											"application/json");
    									// dto.setCreator(java.net.URLEncoder.encode(dto.getCreator(),
    									// "utf-8"));
    									// read response
    									/* for Get request */
    									urlConnection.setRequestMethod("POST");
    									urlConnection.setDoOutput(true);

    									DataOutputStream wr = new DataOutputStream(
    											urlConnection.getOutputStream());

    									String jsonString = "{\"question\":\""
    											+ java.net.URLEncoder.encode(
    													tmpinput, "utf-8") + "\"}";
    									wr.writeBytes(jsonString);
    									wr.flush();
    									wr.close();
    									// try to get response

    									int statusCode = urlConnection
    											.getResponseCode();
    									if (statusCode == 200) {
    										InputStreamReader inputStreamReader = new InputStreamReader(
    												urlConnection.getInputStream());
    										StringBuilder tmpsb = new StringBuilder();
    										BufferedReader buffer = new BufferedReader(
    												inputStreamReader); // 获取输入流对象
    										String inputLine = null;
    										while ((inputLine = buffer.readLine()) != null) {
    											tmpsb.append(inputLine)
    													.append("\n");
    										}
    										inputStreamReader.close(); // 关闭字符输入流
    										String response = tmpsb.toString();
    										JSONTokener tokener = new JSONTokener(
    												response);
    										JSONObject joResult = new JSONObject(
    												tokener);
    										if (joResult.has("answer"))
    											tmpanswer = joResult
    													.getString("answer");
    									}

    								} catch (Exception e) {
    									e.printStackTrace();
    								} finally {
    									if (inputStream != null) {
    										try {
    											inputStream.close();
    										} catch (IOException e) {
    											e.printStackTrace();
    										}
    									}
    									if (urlConnection != null) {
    										urlConnection.disconnect();
    									}
    								}

    								Log.i(TAG, tmpanswer);
    								return tmpanswer;
    							} else
    								return "";
    						}

    						protected void onPostExecute(String result) {
    							Log.i(TAG, result);
    							// text
    							speak(result);
    						};
    					}.execute(mRecoBuffer);
    				}
        		}
        		// 显示
        		//((EditText)findViewById(R.id.isr_text)).setText(text);
        		showTip(text);
        		mRecoBuffer ="";
        	} else {
        		Log.d(TAG, "recognizer result : null");
        	}	
        }
        
        @Override
        public void onEndOfSpeech() {
        	// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
        	showTip("结束说话");
        }
        
        @Override
        public void onBeginOfSpeech() {
        	// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
        	showTip("开始说话");
        }

		@Override
		public void onError(SpeechError error) {
			showTip("onError Code："	+ error.getErrorCode());
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}

    };
    
	

	private void showTip(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mToast.setText(str);
				mToast.show();
			}
		});
	}

	/**
	 * 参数设置
	 * @return
	 */
	public boolean setParam(){
		boolean result = false;
		//设置识别引擎
		mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
		//设置返回结果为json格式
		mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");

		if("cloud".equalsIgnoreCase(mEngineType))
		{
			String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
			if(TextUtils.isEmpty(grammarId))
			{
				result =  false;
			}else {
				//设置云端识别使用的语法id
				mAsr.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
				result =  true;
			}
		}

		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mAsr.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/asr.wav");
		return result;
	}
	
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if( null != mAsr ){
			// 退出时释放连接
			mAsr.cancel();
			mAsr.destroy();
		}
		if( null != mTts ){
			mTts.stopSpeaking();
			// 退出时释放连接
			mTts.destroy();
		}
	}
	
	@Override
	protected void onResume() {
		//移动数据统计分析
		FlowerCollector.onResume(MainActivity.this);
		FlowerCollector.onPageStart(TAG);
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		//移动数据统计分析
		FlowerCollector.onPageEnd(TAG);
		FlowerCollector.onPause(MainActivity.this);
		super.onPause();
	}


	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId())
		{
		case R.id.button_start:
			int ret = mAsr.startListening(mRecognizerListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("识别失败,错误码: " + ret);
			}
			break;
		case R.id.button_stop:
			mAsr.stopListening();
			showTip("停止识别");
			break;
			//	mAsr.cancel();
			//showTip("取消识别");
		case R.id.button_testtts:
			speak("这是语音合成测试");
			break;
		}
	}
	
	private void speak(String text){
		int code = mTts.startSpeaking(text, mTtsListener);
//		/** 
//		 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//		 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//		*/
//		String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//		int code = mTts.synthesizeToUri(text, path, mTtsListener);
		
		if (code != ErrorCode.SUCCESS) {
			showTip("语音合成失败,错误码: " + code);
		}
	}
    	
}
