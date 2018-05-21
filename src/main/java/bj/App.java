package bj;

import bj.jmplayer.JmPlayer;
import bj.jmplayer.MediaInfo;
import bj.jmplayer.PlayerListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

@SpringBootApplication
public class App extends JFrame {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    private final Logger logger = Logger.getLogger(getClass().getName());
    private JmPlayer player;
    private JSlider progressSlider;
    private JTable playlistTable;
    private JLabel progressLabel;
    private JLabel lyricList;
    private PlaylistModel playlistModel;
    private MediaInfo currentMedia;
    private int currentProgress = 0;
    private List<Integer> playHistory = new ArrayList<>();
    private int playHistoryCursor = -1;

    public App() {
        setTitle("app");
        initStyle();
        initLayout();
        initPlayer();
        loadPlaylist();
    }

    private void initStyle() {
        try {
            setUIFont(new FontUIResource("Consolas", Font.PLAIN, 18));
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Slider.paintValue", false);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            logger.severe(e.getMessage());
        }
    }

    private void initLayout() {
        Container root = new Box(BoxLayout.Y_AXIS) {{
            setBorder(new EmptyBorder(10, 10, 10, 10));
            add(new Box(BoxLayout.X_AXIS) {{
                add(new JScrollPane() {{
                    setViewportView(
                            new JTable(new PlaylistModel() {{
                                playlistModel = this;
                            }}) {{
                                playlistTable = this;
                                setRowHeight(25);
                                setFont(new FontUIResource("Noto", Font.PLAIN, 16));
                                setTableHeader(null);
                                setShowGrid(false);
                                getColumnModel().getColumn(0).setMaxWidth(80);
                                addMouseListener(new MouseAdapter() {
                                    @Override
                                    public void mouseClicked(MouseEvent mouseEvent) {
                                        if (mouseEvent.getClickCount() == 2) {
                                            int row = playlistTable.rowAtPoint(mouseEvent.getPoint());
                                            MediaInfo mediaInfo = playlistModel.getRow(row);
                                            playSelected(row);
                                        }
                                    }
                                });
                            }}
                    );
                }});
                add(new JScrollPane() {{
                    setViewportView(new JLabel("[LYRIC]") {{
                        lyricList = this;
                        setFont(new FontUIResource("Noto", Font.PLAIN, 20));
                        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                        setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                    }});
                }});
                setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            }});
            add(new Box(BoxLayout.X_AXIS) {{
                add(new JButton() {{
                    setPreferredSize(new Dimension(48, 48));
                    setContentAreaFilled(false);
                    ImageIcon icon = new ImageIcon("/usr/share/icons/Adwaita/48x48/actions/media-playback-start.png");
                    ImageIcon newIcon = new ImageIcon(icon.getImage().getScaledInstance(48, 48, Image.SCALE_SMOOTH));
                    setIcon(newIcon);
                    addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent actionEvent) {
                            try {
                                player.pause();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }});
                add(new JButton("Prev") {{
                    addActionListener(actionEvent -> {
                        playPrev();
                    });
                }});
                add(new JButton("Next") {{
                    addActionListener(actionEvent -> {
                        playNext();
                    });
                }});
                add(new JSlider() {{
                    progressSlider = this;
                    setMaximum(10);
                    addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent changeEvent) {
                            if (progressSlider.getValue() != currentProgress) {
                                player.skip(progressSlider.getValue());
                            }
                        }
                    });
                    addMouseWheelListener(mouseWheelEvent -> {
                        if (mouseWheelEvent.getWheelRotation() == -1) {
                            increaseProgress(5);
                        } else {
                            increaseProgress(-5);
                        }
                    });
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            Point p = e.getPoint();
                            double percent = p.x / ((double) getWidth());
                            int range = getMaximum() - getMinimum();
                            double newVal = range * percent;
                            int result = (int) (getMinimum() + newVal);
                            setValue(result);
                        }
                    });
                }});
                add(new JLabel() {{
                    progressLabel = this;
                }});
            }});
            setDropTarget(new DropTarget() {
                @SuppressWarnings("unchecked")
                @Override
                public synchronized void drop(DropTargetDropEvent event) {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    try {
                        List<File> droppedFiles = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        System.out.println("files: " + droppedFiles);
                        for (File file : droppedFiles) {
                            String filename = file.getName();
                            if (filename.endsWith(".mp3") || filename.endsWith(".wma")) {
                                addFileToPlaylist(file);
                            }
                            savePlaylist();
                        }
                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }};
        updateProgress();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 720);
        setLocationRelativeTo(null);
        setVisible(true);
        setContentPane(root);
    }

    private void updateProgress() {
        int total = currentMedia == null ? 0 : (int) currentMedia.getLength();
        int current = currentProgress;
        progressLabel.setText(String.format("%02d:%02d/%02d:%02d", current / 60, current % 60, total / 60, total % 60));
        progressSlider.setMaximum(total);
        progressSlider.setValue(current);
    }

    private void increaseProgress(int progress) {
        progressSlider.setValue(progressSlider.getValue() + progress);
    }

    private void initPlaylist() {
    }

    private void addFileToPlaylist(File file) {
        String filename = file.getAbsolutePath();
        MediaInfo mediaInfo = player.getMediaInfo(filename);
        logger.info(String.format("Parsed: [%s|%s](%s)", mediaInfo.getAuthor(), mediaInfo.getTitle(), mediaInfo.getFilename()));

        PlaylistModel model = (PlaylistModel) playlistTable.getModel();
        model.addRow(mediaInfo);
        playlistTable.revalidate();
        JScrollBar scrollBar = ((JScrollPane) playlistTable.getParent().getParent()).getVerticalScrollBar();
        scrollBar.setValue(scrollBar.getMaximum());
    }

    private void savePlaylist() {
        PlaylistModel model = (PlaylistModel) playlistTable.getModel();
        Vector<MediaInfo> tableData = model.getTableData();
        File file = new File("rawsteelj.playlist");
        try {
            PrintWriter writer = new PrintWriter(file);
            for (MediaInfo item : tableData) {
                String str = String.format("%s|%s|%d|%s\n", item.getAuthor(), item.getTitle(), (int) item.getLength(), item.getFilename());
                System.out.println(str);
                writer.write(str);
            }
            writer.close();
        } catch (FileNotFoundException e) {
            logger.severe(e.getMessage());
        }
    }

    private void loadPlaylist() {
        PlaylistModel model = (PlaylistModel) playlistTable.getModel();
        File file = new File("rawsteelj.playlist");
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split("\\|");
                MediaInfo mediaInfo = new MediaInfo();
                mediaInfo.setAuthor(parts[0]);
                mediaInfo.setTitle(parts[1]);
                mediaInfo.setLength(Float.parseFloat(parts[2]));
                mediaInfo.setFilename(parts[3]);
                model.addRow(mediaInfo);
                playlistTable.revalidate();
            }
        } catch (FileNotFoundException e) {
            logger.severe(e.getMessage());
        }
    }

    private void initPlayer() {
        player = new JmPlayer();
        player.setPlayerListener(new PlayerListener() {
            @Override
            public void onProgress(float progress) {
                currentProgress = (int) progress;
                updateProgress();
            }
        });
    }

    private void play(MediaInfo mediaInfo) {
        player.play(mediaInfo.getFilename());

        String musicFilename = mediaInfo.getFilename();
        String lyricFilename = musicFilename.replaceFirst("\\.[^.]+$", ".lrc");
        File lyricFile = new File(lyricFilename);
        try {
            String lyric = new Scanner(new FileInputStream(lyricFile), "GBK").useDelimiter("\\Z").next();
            lyricList.setText(String.format("<html>%s</html>", lyric.replaceAll("\n", "<br>")));
        } catch (FileNotFoundException e) {
            lyricList.setText("Lyric not exist");
        }
    }

    private void play(int indexToPlay) {
        currentMedia = getPlayList().get(indexToPlay);
        currentProgress = 0;
        updateProgress();

        Vector<MediaInfo> playlist = getPlayList();
        playlistTable.setRowSelectionInterval(indexToPlay, indexToPlay);
        JScrollBar scrollBar = ((JScrollPane) playlistTable.getParent().getParent()).getVerticalScrollBar();
        scrollBar.setValue(scrollBar.getMaximum() * indexToPlay / playlist.size());
        play(playlist.get(indexToPlay));
    }

    private void playNext() {
        int size = playHistory.size();
        if (playHistoryCursor >= 0 && playHistoryCursor < size - 1) {
            playHistoryCursor += 1;
            play(playHistory.get(playHistoryCursor));
        } else {
            Vector<MediaInfo> playlist = getPlayList();
            int indexToPlay = playlist.indexOf(currentMedia);
            while (indexToPlay == playlist.indexOf(currentMedia)) {
                indexToPlay = new Random().nextInt(playlist.size());
            }
            play(indexToPlay);
            playHistory.add(indexToPlay);
            playHistoryCursor = playHistory.size() - 1;
        }
    }

    private void playSelected(int index) {
        play(index);
        playHistory.add(index);
        playHistoryCursor = playHistory.size() - 1;
    }

    private void playPrev() {
        int size = playHistory.size();
        if (playHistoryCursor > 0 && playHistoryCursor < size) {
            playHistoryCursor -= 1;
            play(playHistory.get(playHistoryCursor));
        }
    }

    private Vector<MediaInfo> getPlayList() {
        PlaylistModel model = (PlaylistModel) playlistTable.getModel();
        return model.getTableData();
    }

    private void skip(int seconds) {
        player.skip(seconds);
    }

    private static void setUIFont(javax.swing.plaf.FontUIResource f) {
        java.util.Enumeration keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource)
                UIManager.put(key, f);
        }
    }

    class PlaylistModel extends AbstractTableModel {
        String[] columnNames = new String[]{"No", "Author", "Title"};
        Vector<MediaInfo> tableData = new Vector<>();

        @Override
        public int getRowCount() {
            return tableData.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        public MediaInfo getRow(int i) {
            return tableData.get(i);
        }

        public Vector<MediaInfo> getTableData() {
            return tableData;
        }

        @Override
        public Object getValueAt(int i, int i1) {
            MediaInfo mediaInfo = tableData.get(i);
            if (i1 == 0) {
                return String.valueOf(i + 1);
            } else if (i1 == 1) {
                return mediaInfo.getAuthor();
            } else if (i1 == 2) {
                return mediaInfo.getTitle();
            } else {
                return "NULL";
            }
        }

        void addRow(MediaInfo mediaInfo) {
            tableData.add(mediaInfo);
        }
    }
}
