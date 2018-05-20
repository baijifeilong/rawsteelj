package bj.jmplayer;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by BaiJiFeiLong@gmail.com at 18-5-20 上午9:45
 */
public class JmPlayer {

    private final Logger logger = Logger.getLogger(getClass().getName());
    private static final List<String> CMD_GET_MEDIA_INFO = Arrays.asList("mplayer", "-identify", "-frames", "0");
    private static final List<String> CMD_PLAY_MUSIC = Collections.singletonList("mplayer");

    private Process process;
    private PlayerMonitor playerMonitor;
    private PlayerListener playerListener;
    private String currentMediaPath;

    public MediaInfo getMediaInfo(String mediaPath) {
        try {
            List<String> cmd = new ArrayList<>(CMD_GET_MEDIA_INFO);
            cmd.add(mediaPath);
            Process process = new ProcessBuilder(cmd).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            MediaInfo mediaInfo = new MediaInfo();
            mediaInfo.setFilename(mediaPath);
            float length = 0;
            String title = null;
            String author = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ID_LENGTH")) {
                    length = Float.parseFloat(line.substring("ID_LENGTH=".length()));
                } else if (line.startsWith(" title:")) {
                    title = line.substring(" title: ".length());
                } else if (line.startsWith(" author:")) {
                    author = line.substring(" author: ".length());
                }
            }
            if (author == null || title == null) {
                String shortFilename = new File(mediaPath).getName();
                shortFilename = shortFilename.substring(0, shortFilename.lastIndexOf("."));
                String[] parts = shortFilename.split("-");
                assert parts.length >= 2;
                author = parts[0];
                title = parts[1];
            }
            mediaInfo.setLength(length);
            mediaInfo.setTitle(title);
            mediaInfo.setAuthor(author);
            return mediaInfo;
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return new MediaInfo();
        }
    }

    public void setPlayerListener(PlayerListener playerListener) {
        this.playerListener = playerListener;
    }

    private void play(String mediaPath, int startPosition) {
        try {
            stop();
            currentMediaPath = mediaPath;
            List<String> cmd = new ArrayList<String>(CMD_PLAY_MUSIC) {{
                add(mediaPath);
                add("-ss");
                add(String.valueOf(startPosition));
            }};
            process = new ProcessBuilder(cmd).start();
            playerMonitor = new PlayerMonitor(process.getInputStream(), playerListener);
            new Thread(playerMonitor).start();
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    public void play(String mediaPath) {
        play(mediaPath, 0);
    }

    public void skip(int seconds) {
        play(currentMediaPath, seconds);
    }

    public void stop() {
        if (process != null) {
            try {
                playerMonitor.deactive();
                process.getOutputStream().write("q".getBytes());
                process.getOutputStream().flush();
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    public void pause() throws IOException {
        process.getOutputStream().write("p".getBytes());
        process.getOutputStream().flush();
    }

    public class PlayerMonitor implements Runnable {

        private InputStream inputStream;
        private PlayerListener playerListener;
        private boolean running;

        public PlayerMonitor(InputStream inputStream, PlayerListener playerListener) {
            if (playerListener == null) {
                playerListener = new PlayerListener() {
                    @Override
                    public void onProgress(float progress) {
                    }
                };
            }
            this.inputStream = inputStream;
            this.playerListener = playerListener;
            running = true;
        }

        public void deactive() {
            this.running = false;
        }

        @Override
        public void run() {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            int lastSecond = 0;
            Pattern pattern = Pattern.compile("A:.+?(\\d+).+");
            try {
                while (running && (line = bufferedReader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        int second = Integer.parseInt(matcher.group(1));
                        if (second != lastSecond) {
                            playerListener.onProgress(second);
                        }
                        lastSecond = second;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
