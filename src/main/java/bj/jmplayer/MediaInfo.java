package bj.jmplayer;

/**
 * Created by BaiJiFeiLong@gmail.com at 18-5-20 下午6:05
 */
public class MediaInfo {
    private String filename;
    private float length;
    private String title;
    private String author;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public float getLength() {
        return length;
    }

    public void setLength(float length) {
        this.length = length;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return String.format("%s\t%s", author, title);
    }
}
