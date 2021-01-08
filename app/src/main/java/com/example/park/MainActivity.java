package com.example.park;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class MainActivity extends AppCompatActivity {
    private Button startButton;
    private EditText IPText;
    private boolean isConnecting=false;
    private Thread mThreadClient=null;
    private Socket mSocketClient=null;
    private PrintWriter mPrintWriterClient=null;
    private  String res="";
    private TextView recvText,letf_text;
    private Switch switch_in,switch_out;//出入闸开关
    private  Chronometer timer1,timer2,timer3,timer4,timer5,timer6;
    private String []send_order={"1\r\n","2\r\n","3\r\n","4\r\n"};//发送指令
    private ImageView car_1,car_2,car_3,car_4,car_5,car_6;
    private int sum=6;//车位数量
    private boolean []flag={false,false,false,false,false,false};
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        );
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
        IPText= findViewById(R.id.IPText);//IP地址和端口号
        IPText.setText("192.168.1.127:8080");
        startButton= findViewById(R.id.StartConnect);//连接和停止按钮
        startButton.setOnClickListener(StartClickListener);

        recvText= findViewById(R.id.tv1);//连接状态显示
        letf_text=findViewById(R.id.left);//剩余车位显示
        letf_text.setText("当前剩余车位："+sum);
        //车位显示
        car_1=findViewById(R.id.car_1);
        car_2=findViewById(R.id.car_2);
        car_3=findViewById(R.id.car_3);
        car_4=findViewById(R.id.car_4);
        car_5=findViewById(R.id.car_5);
        car_6=findViewById(R.id.car_6);

        //计时显示
        timer1 = findViewById(R.id.timer1);
        timer2 = findViewById(R.id.timer2);
        timer3= findViewById(R.id.timer3);
        timer4= findViewById(R.id.timer4);
        timer5= findViewById(R.id.timer5);
        timer6 = findViewById(R.id.timer6);

        switch_in =findViewById(R.id.switch_into);//入闸开关
        switch_out=findViewById(R.id.switch_out);//出闸开关
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switch_in.setShowText(true);
            switch_out.setShowText(true);
        }

        switch_in.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    switch_in.setSwitchTextAppearance(MainActivity.this,R.style.s_true);
                    switch_in.setShowText(true);
                    if (send(send_order[0],-1)){
                        showDialog("开入闸");
                    }else{
                        switch_in.setChecked(false);
                    }
                }else{

                    switch_in.setSwitchTextAppearance(MainActivity.this,R.style.s_false);
                    switch_in.setShowText(true);
                    if (send(send_order[1],-2)){
                        showDialog("关入闸");
                    }else{
                        switch_in.setChecked(false);
                    }
                }
            }
        });
        switch_out.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    switch_out.setSwitchTextAppearance(MainActivity.this,R.style.s_true);
                    switch_out.setShowText(true);
                    if (send(send_order[2],-1)){
                        showDialog("开出闸");
                    }else{
                        switch_out.setChecked(false);
                    }
                }else{
                    switch_out.setSwitchTextAppearance(MainActivity.this,R.style.s_false);
                    switch_out.setShowText(true);
                    if (send(send_order[3],-2)){
                       // flag=false;
                        showDialog("关出闸");
                    }else{
                        switch_out.setChecked(false);
                    }
                }
            }
        });

    }
    //连接到智能衣柜
    private View.OnClickListener StartClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            if(isConnecting)
            {
                isConnecting=false;
                if(mSocketClient!=null)
                {
                    try{
                        mSocketClient.close();
                        mSocketClient = null;
                        if (mPrintWriterClient!=null){
                            mPrintWriterClient.close();
                            mPrintWriterClient = null;
                        }
                        mThreadClient.interrupt();
                        startButton.setText("开始连接");
                        IPText.setEnabled(true);//可以输入ip和端口号
                        recvText.setText("断开连接\n");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }else
            {
                mThreadClient = new Thread(mRunnable);
                mThreadClient.start();
            }
        }
    };

    private Runnable mRunnable = new Runnable() {

        @Override
        public void run() {
            String msgText = IPText.getText().toString();
            if(msgText.length()<=0)
            {
                Message msg = new Message();
                msg.what = 5;
                mHandler.sendMessage(msg);
                return;
            }
            int start = msgText.indexOf(":");
            if((start==-1)||(start+1>=msgText.length()))
            {
                Message msg = new Message();
                msg.what = 6;
                mHandler.sendMessage(msg);
                return;
            }
            String sIP= msgText.substring(0,start);
            String sPort = msgText.substring(start+1);
            int port = Integer.parseInt(sPort);

            BufferedReader mBufferedReaderClient;
            try
            {
                //连接服务器
                mSocketClient = new Socket();
                SocketAddress socAddress = new InetSocketAddress(sIP, port);
                mSocketClient.connect(socAddress, 2000);//设置超时时间为2秒
                //取得输入、输出流
                mBufferedReaderClient =new BufferedReader(new InputStreamReader(mSocketClient.getInputStream()));
                mPrintWriterClient=new PrintWriter(mSocketClient.getOutputStream(),true);
                Message msg = new Message();
                msg.what = 1;
                mHandler.sendMessage(msg);

            }catch (Exception e) {
                Message msg = new Message();
                msg.what = 2;
                mHandler.sendMessage(msg);
                return;
            }
            char[] buffer = new char[256];
            int count ;

            while(true)
            {
                try
                {
                    if((count = mBufferedReaderClient.read(buffer))>0)
                    {
                        res = getInfoBuff(buffer,count)+"\n";//接收到的内容
                        Message msg = new Message();
                        msg.what = 4;
                        mHandler.sendMessage(msg);
                    }
                }catch (Exception e) {
                    // TODO: handle exception
                    Message msg = new Message();
                    msg.what = 3;
                    mHandler.sendMessage(msg);
                }
            }
        }
    };

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler()
    {
        @SuppressLint("SetTextI18n")
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if(msg.what==4)
            {
                char []arrs;
                arrs=res.toCharArray();//接收来自服务器的字符串
                if (arrs.length>=7) {
                    if (arrs[3]=='1'&&arrs[5]=='i'){
                        car_1.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[0]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer1.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer1.getBase()) / 1000 / 60);
                            timer1.setFormat("0"+ hour +":%s");
                            timer1.start();//开始计时
                        }

                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='1'&&arrs[5]=='o'){
                        car_1.setImageResource(R.drawable.caroff);
                        if (flag[0]){//该车位有车停过，现在离开
                            flag[0]=false;//该车位目前空余
                            sum++;
                            timer1.stop();//停止计时
                            timer1.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }

                            letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='2'&&arrs[5]=='i'){
                        car_2.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[1]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer2.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer2.getBase()) / 1000 / 60);
                            timer2.setFormat("0"+ hour +":%s");
                            timer2.start();//开始计时

                        }
                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='2'&&arrs[5]=='o'){
                        car_2.setImageResource(R.drawable.caroff);
                        if (flag[1]){
                            flag[1]=false;
                            sum++;
                            timer2.stop();//停止计时
                            timer2.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }
                            letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='3'&&arrs[5]=='i'){
                        car_3.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[2]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer3.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer3.getBase()) / 1000 / 60);
                            timer3.setFormat("0"+ hour +":%s");
                            timer3.start();//开始计时
                        }
                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='3'&&arrs[5]=='o'){
                        car_3.setImageResource(R.drawable.caroff);
                        if (flag[2]){
                            flag[2]=false;
                            sum++;
                            timer3.stop();//停止计时
                            timer3.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }
                            letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='4'&&arrs[5]=='i'){
                        car_4.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[3]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer4.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer4.getBase()) / 1000 / 60);
                            timer4.setFormat("0"+ hour +":%s");
                            timer4.start();//开始计时
                        }
                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='4'&&arrs[5]=='o'){
                        car_4.setImageResource(R.drawable.caroff);
                        if ( flag[3]){
                            flag[3]=false;
                            sum++;
                            timer4.stop();//停止计时
                            timer4.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }
                            letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='5'&&arrs[5]=='i'){
                        car_5.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[4]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer5.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer5.getBase()) / 1000 / 60);
                            timer5.setFormat("0"+ hour +":%s");
                            timer5.start();//开始计时
                        }
                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='5'&&arrs[5]=='o'){
                        car_5.setImageResource(R.drawable.caroff);
                        if (flag[4]){
                            flag[4]=false;
                            sum++;
                            timer5.stop();//停止计时
                            timer5.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }
                            letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='6'&&arrs[5]=='i'){
                        car_6.setImageResource(R.drawable.caron);
                        if (sum>0){
                            flag[5]=true;//有车进来
                            sum--;
                            //建议计时放在终端（停车场系统），然后APP这边连接之后再获取时间开始累加计时。此处暂时先让APP计时。
                            timer6.setBase(SystemClock.elapsedRealtime());//计时器清零
                            int hour = (int) ((SystemClock.elapsedRealtime() - timer6.getBase()) / 1000 / 60);
                            timer6.setFormat("0"+ hour +":%s");
                            timer6.start();//开始计时
                        }
                        letf_text.setText("当前剩余车位："+sum);
                    }
                    else if (arrs[3]=='6'&&arrs[5]=='o'){
                        car_6.setImageResource(R.drawable.caroff);
                        if ( flag[5]){
                            flag[5]=false;
                            sum++;
                            timer6.stop();//停止计时
                            timer6.setBase(SystemClock.elapsedRealtime());//计时器清零
                        }
                            letf_text.setText("当前剩余车位："+sum);
                    }
                }else {
                    showDialog("收到格式错误的数据:"+res);
                }
            }else if (msg.what==2){
                showDialog("连接失败，服务器走丢了");
                startButton.setText("开始连接");


            }else if (msg.what==1){
                showDialog("连接成功！");
                recvText.setText("已连接停车场\n");
                IPText.setEnabled(false);//锁定ip地址和端口号
                isConnecting = true;
                startButton.setText("停止连接");
            }else if (msg.what==3){
                recvText.setText("已断开连接\n");
//                car_1.setImageResource(R.drawable.caroff);
//                car_2.setImageResource(R.drawable.caroff);
//                car_3.setImageResource(R.drawable.caroff);
//                car_4.setImageResource(R.drawable.caroff);
//                car_5.setImageResource(R.drawable.caroff);
//                car_6.setImageResource(R.drawable.caroff);
//                timer1.stop();//停止计时
//                timer2.stop();//停止计时
//                timer3.stop();//停止计时
//                timer4.stop();//停止计时
//                timer5.stop();//停止计时
//                timer6.stop();//停止计时
//                timer1.setBase(SystemClock.elapsedRealtime());//计时器清零
//                timer2.setBase(SystemClock.elapsedRealtime());//计时器清零
//                timer3.setBase(SystemClock.elapsedRealtime());//计时器清零
//                timer4.setBase(SystemClock.elapsedRealtime());//计时器清零
//                timer5.setBase(SystemClock.elapsedRealtime());//计时器清零
//                timer6.setBase(SystemClock.elapsedRealtime());//计时器清零
//                letf_text.setText("请先连接停车场哦！");

            }else if (msg.what==5){
                recvText.setText("IP和端口号不能为空\n");
            }
            else if (msg.what==6){
                recvText.setText("IP地址不合法\n");
            }
        }
    };
    private  void showDialog(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create().show();
    }
    private String getInfoBuff(char[] buff,int count)
    {
        char[] temp = new char[count];
        System.arraycopy(buff, 0, temp, 0, count);
        return new String(temp);
    }

    private boolean send(String msg,int position){
        if(isConnecting&&mSocketClient!=null){
            if ((position==-1)||(position==-2)){
                try
                {
                    mPrintWriterClient.print(msg);
                    mPrintWriterClient.flush();
                    return true;
                }catch (Exception e) {
                    // TODO: handle exception
                    Toast.makeText(MainActivity.this, "发送异常"+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

        }else{
            showDialog("您还没有连接停车场呢！");
        }
        return false;
    }

}

