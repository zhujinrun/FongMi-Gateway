package android.app;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

public class Activity extends Context {

    private Intent intent = new Intent();

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public void setContentView(int layoutResID) {
    }

    public void setContentView(View view) {
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
    }

    public <T extends View> T findViewById(int id) {
        return null;
    }

    public android.content.ComponentName getComponentName() {
        return new android.content.ComponentName(getPackageName(), getClass().getName());
    }

    public android.view.Window getWindow() {
        return new android.view.Window();
    }

    public void finish() {
    }

    public void startActivity(Intent intent) {
    }

    public void startActivityForResult(Intent intent, int requestCode) {
    }

    public void setTitle(CharSequence title) {
    }

    public void setTitle(int titleId) {
    }

    public void onBackPressed() {
    }

    public void runOnUiThread(Runnable action) {
        action.run();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    }

    protected void onCreate(android.os.Bundle savedInstanceState) {
    }

    protected void onDestroy() {
    }

    protected void onPause() {
    }

    protected void onResume() {
    }

    protected void onStart() {
    }

    protected void onStop() {
    }

    protected void onSaveInstanceState(android.os.Bundle outState) {
    }

    public int checkSelfPermission(java.lang.String p0) { return 0; }
    public android.view.WindowManager getWindowManager() { return null; }
    public boolean isDestroyed() { return false; }
    public boolean isFinishing() { return false; }
    public void requestPermissions(java.lang.String[] p0, int p1) {}
}
