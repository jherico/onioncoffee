package net.sf.onioncoffee.app;

public interface ProgressCallback {
    public void setStatusText(String text);
    public void setProgressPercent(float percent);
    public void setProgressPercent(String text, float percent);
    public float getProgressPercent();
}
