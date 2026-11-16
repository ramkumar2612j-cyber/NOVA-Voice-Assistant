package org.vosk;
public class Recognizer {
    public Recognizer(Model m, float rate) {}
    public Recognizer(Model m, float rate, String grammar) {}
    public Recognizer(Model m, float rate, SpeakerModel sm) {}
    public boolean acceptWaveForm(byte[] b, int n) { return false; }
    public boolean acceptWaveForm(short[] b, int n) { return false; }
    public String getResult() { return ""; }
    public String getPartialResult() { return ""; }
    public String getFinalResult() { return ""; }
    public void reset() {}
    public void setWords(boolean b) {}
    public void close() {}
}
