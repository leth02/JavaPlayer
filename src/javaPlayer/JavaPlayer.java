import java.awt.*;
import java.awt.event.*;
import java.text.AttributedString;
import java.text.AttributedCharacterIterator;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.event.*;

import javax.sound.sampled.*;

import java.io.File;
import javax.swing.filechooser.*;
import javax.swing.filechooser.FileFilter;
import java.util.Vector;

public class JavaPlayer extends JPanel implements Runnable, LineListener, ActionListener {

    final int bufferSize = 16384;
    PlaybackMonitor playbackMonitor = new PlaybackMonitor();

    Vector sounds = new Vector();
    Thread thread;
    boolean midiEOM, audioEOM;

    Object currentSound;
    String currentName;

    double duration;
    int num;

    boolean bump;
    boolean paused = false;
    JButton startB, pauseB, loopB, prevB, nextB;

    JTable table;
    JSlider panSlider, gainSlider;
    JSlider seekSlider;

    JukeTable jukeTable;
    Loading loading;

    String errStr;
    JukeControls controls;

    JFileChooser fileChooser;
    JMenuBar bigMenuBar;
    JMenu bigMenu;
    JMenuItem openItem, exitItem;

    public JavaPlayer() {
        setLayout(new BorderLayout());
        setBorder (new EmptyBorder(5,5,5,5));

        bigMenuBar = new JMenuBar();
        bigMenu = (JMenu) bigMenuBar.add(new JMenu("File"));

        openItem = bigMenu.add(new JMenuItem("Open file"));
        openItem.addActionListener(this);

        exitItem = bigMenu.add(new JMenuItem("Exit"));
        exitItem.addActionListener(this);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        jukeTable = new JukeTable(), controls = new JukeControls());
        splitPane.setContinuousLayout(true);
        
        add("North", bigMenuBar);
        add(splitPane);
        
    }
    
    public void run() {
        do {
            table.scrollRectToVisible(new Rectangle(0,0,1,1));
            for (; num < sounds.size() && thread != null; num++) {
                table.scrollRectToVisible(new Rectangle(0,(num+2)*(table.getRowHeight()+table.getRowMargin()),1,1));
                table.setRowSelectionInterval(num, num);
                if( loadSound(sounds.get(num)) == true ) {
                    playSound();
                }
                // take a little break between sounds
                try { thread.sleep(222); } catch (Exception e) {break;}
            }
            num = 0;
        } while (loopB.isSelected() && thread != null);

        if (thread != null) {
            startB.doClick();
        }
        thread = null;
        currentName = null;
        currentSound = null;
        playbackMonitor.repaint();
    }
    
    public void actionPerformed(ActionEvent e){
        Object object = e.getSource();

        if (object instanceof JMenuItem) {
            JMenuItem mi = (JMenuItem) object;

            if (mi.getText().equals("Open file")) {
                loadJuke();
            }

            else if (mi.getText().equals("Exit")) {
                System.exit(0);
            }
        }
    }

    public void open(){
        System.out.println("Start the program");
    }

    // Invoked by the "Open" button to load sounds into the program
    public void loadJuke() {
        fileChooser = new JFileChooser();

        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        FileNameExtensionFilter filter = new FileNameExtensionFilter("WAV files", "wav");

        int returnVal = fileChooser.showOpenDialog(this.getParent());
        if (returnVal == JFileChooser.APPROVE_OPTION) {

            File file = fileChooser.getSelectedFile();

            // A folder of files is chosen
            if (file != null && file.isDirectory()) {

                File [] fileList = file.listFiles();

                if (fileList.length > 0) {

                    for (File sound: fileList) {
                            sounds.add(sound);
                    }
                }
            }
            // A file is chosen
            else {sounds.add(file);}
            
            jukeTable.tableChanged();
        }
    }

    public boolean loadSound(Object object) {

        duration = 0.0;
        (loading = new Loading()).start();

        if (object instanceof File) {
                currentName = ((File) object).getName();
                playbackMonitor.repaint();

                try {
                        currentSound = AudioSystem.getAudioInputStream((File) object);
                }
                catch (Exception e) {
                        e.printStackTrace();
                        return false;
                }
        }

        loading.interrupt();

        if (currentSound instanceof AudioInputStream) {
            try {
                AudioInputStream stream = (AudioInputStream) currentSound;
                AudioFormat format = stream.getFormat();

                // Get the Line that is suitable to the format of the audio
                DataLine.Info info = new DataLine.Info(
                                        Clip.class,
                                        stream.getFormat(),
                                        ((int) stream.getFrameLength() * 
                                                format.getFrameSize()));

                Clip clip = (Clip) AudioSystem.getLine(info);
                System.out.println(stream.getFormat().toString());
                clip.addLineListener(this);
                clip.open(stream);
                currentSound = clip;
                seekSlider.setMaximum((int) stream.getFrameLength());

            }
            catch (Exception e) {
                    e.printStackTrace();
                    currentSound = null;
                    return false;
            }
        }

        seekSlider.setValue(0);
        seekSlider.setEnabled(true);
        panSlider.setEnabled(true);
        gainSlider.setEnabled(true);

        duration = getDuration();

        return true;

    }


    public void playSound() {

        playbackMonitor.start();
        setGain();
        setPan();
        midiEOM = audioEOM = bump = false;

            if (currentSound instanceof Clip && thread != null) {
            Clip clip = (Clip) currentSound;
            clip.start();
            try { thread.sleep(99); } catch (Exception e) { }
            while ((paused || clip.isActive()) && thread != null && !bump) {
                try { thread.sleep(99); } catch (Exception e) {break;}
            }
            clip.stop();
            clip.close();
            }
        currentSound = null;
        playbackMonitor.stop();
    }

    public void setPan() {
        int value = panSlider.getValue();

        if (currentSound instanceof Clip) {
            try {
                Clip clip = (Clip) currentSound;
                FloatControl panControl = (FloatControl) clip.getControl(FloatControl.Type.PAN);
                panControl.setValue(value/100.0f);
            }
            catch (Exception e) {
                    e.printStackTrace();
            }
        }
    }

    public void setGain() {
        double value = gainSlider.getValue() / 100.0;
        if (currentSound instanceof Clip) {
            try {
                Clip clip = (Clip) currentSound;
                FloatControl gainControl = 
                    (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) 
                    (Math.log(value==0.0?0.0001:value)/Math.log(10.0)*20.0);
                gainControl.setValue(dB);
            }
            catch (Exception e) {e.printStackTrace();}
        }
    }

    // Get the duration (in second) of the media file
    public double getDuration() {
        double duration = 0.0;

        if (currentSound instanceof Clip) {
            Clip clip = (Clip) currentSound;
            duration = clip.getBufferSize() / (clip.getFormat().getFrameSize() * clip.getFormat().getFrameRate());
        }
        
        return duration;
    }

    // Get the current second when the file plays
    public double getSeconds() {
        double seconds = 0.0;

        if (currentSound instanceof Clip) {
            Clip clip = (Clip) currentSound;
            seconds = clip.getFramePosition() / clip.getFormat().getFrameRate();
        }
        
        return seconds;

    }

    public void update(LineEvent event) {
        if (event.getType() == LineEvent.Type.STOP && !paused) { 
            audioEOM = true;
        }
    }


    private void reportStatus(String msg) {
        if ((errStr = msg) != null) {
            System.out.println(errStr);
            playbackMonitor.repaint();
        }
    }


    public Thread getThread() {
        return thread;
    }

    public void start() {
    	thread = new Thread (this);
    	thread.setName("rain");
    	thread.start();

    }
    
    public void close() {
        
        System.out.println("closing the program");
        if (thread != null && startB != null) {
            startB.doClick(0);
        }
        
    }

    public void stop() {
    	if (thread != null) {
            thread.interrupt();
    	}
    	thread = null;
    }

    class JukeControls extends JPanel implements ActionListener, ChangeListener {

    	public JukeControls() {
    	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JPanel p1 = new JPanel();
            p1.setLayout(new BoxLayout(p1, BoxLayout.Y_AXIS));
            p1.setBorder(new EmptyBorder(10,0,5,0));
            JPanel p2 = new JPanel();
            startB = addButton("Start", p2, true);
            pauseB = addButton("Pause", p2, false);
            p1.add(p2);
            JPanel p3 = new JPanel();
            prevB = addButton("<<", p3, false);
            nextB = addButton(">>", p3, false);
            p1.add(p3);
            add(p1);
    
            JPanel p4 = new JPanel(new BorderLayout());
            EmptyBorder eb = new EmptyBorder(5,20,10,20);
            BevelBorder bb = new BevelBorder(BevelBorder.LOWERED);
            p4.setBorder(new CompoundBorder(eb,bb));
            p4.add(playbackMonitor);
            seekSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
            seekSlider.setEnabled(false);
            seekSlider.addChangeListener(this);
            p4.add("South", seekSlider);
            add(p4);

            JPanel p5 = new JPanel();
            p5.setLayout(new BoxLayout(p5, BoxLayout.X_AXIS));
            p5.setBorder(new EmptyBorder(5,5,10,5));
            panSlider = new JSlider(-100, 100, 0);
            panSlider.addChangeListener(this);
            TitledBorder tb = new TitledBorder(new EtchedBorder());
            tb.setTitle("Pan = 0.0");
            panSlider.setBorder(tb);
            p5.add(panSlider);
            gainSlider = new JSlider(0, 100, 80);
            gainSlider.addChangeListener(this);
            tb = new TitledBorder(new EtchedBorder());
            tb.setTitle("Gain = 80");
            gainSlider.setBorder(tb);
            p5.add(gainSlider);
            add(p5);
    	}

    	private JButton addButton(String name, JPanel panel, boolean state) {
            JButton b = new JButton(name);
            b.addActionListener(this);
            b.setEnabled(state);
            panel.add(b);
            return b;
        }

        public void stateChanged(ChangeEvent e) {
            JSlider slider = (JSlider) e.getSource();
            int value = slider.getValue();

            if (slider.equals(seekSlider)) {
        	if (currentSound instanceof Clip) {
                    ((Clip) currentSound).setFramePosition(value);
                }

                playbackMonitor.repaint();
        	return;
            }

            TitledBorder tb = (TitledBorder) slider.getBorder();
            String s = tb.getTitle();
            if (s.startsWith("Pan")) {
                s = s.substring(0, s.indexOf('=')+1) + s.valueOf(value/100.0);
                if (currentSound != null) {
                    setPan();
                }
            } else if (s.startsWith("Gain")) {
                s = s.substring(0, s.indexOf('=')+1) + s.valueOf(value);
                if (currentSound != null) {
                    setGain();
                }
            } 
            tb.setTitle(s);
            slider.repaint();

        }

        public void setComponentsEnabled(boolean state) {
            seekSlider.setEnabled(state);
            pauseB.setEnabled(state);
            prevB.setEnabled(state);
            nextB.setEnabled(state);
        }

        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            if (button.getText().equals("Start")) {
                paused = false;
                num = table.getSelectedRow();
                num = num == -1 ? 0 : num;
                start();
                button.setText("Stop");
                setComponentsEnabled(true);
            } else if (button.getText().equals("Stop")) {
                paused = false;
                stop();
                button.setText("Start");
                pauseB.setText("Pause");
                setComponentsEnabled(false);
            } else if (button.getText().equals("Pause")) {
                paused = true;
                if (currentSound instanceof Clip) {
                    ((Clip) currentSound).stop();
                }
                playbackMonitor.stop();
                pauseB.setText("Resume");
            } else if (button.getText().equals("Resume")) {
                paused = false;
                if (currentSound instanceof Clip) {
                    ((Clip) currentSound).start();
                }
                playbackMonitor.start();
                pauseB.setText("Pause");
            } else if (button.getText().equals("<<")) {
                paused = false;
                pauseB.setText("Pause");
                num = num-1 < 0 ? sounds.size()-1 : num-2;
                bump = true;
            } else if (button.getText().equals(">>")) {
                paused = false;
                pauseB.setText("Pause");
                num = num+1 == sounds.size() ? -1 : num;
                bump = true;
            }
        }
    }

    public class PlaybackMonitor extends JPanel implements Runnable {

        String welcomeStr = "Welcome to Java Media Player";
        Thread pbThread;

        Color black = new Color(20, 20, 20); 
        Color jfcBlue = new Color(204, 204, 255);
        Color jfcDarkBlue = jfcBlue.darker();
        Font font24 = new Font("serif", Font.BOLD, 24);
        Font font28 = new Font("serif", Font.BOLD, 28);
        Font font42 = new Font("serif", Font.BOLD, 42);
        FontMetrics fm28, fm42;

        public PlaybackMonitor() {
            fm28 = getFontMetrics(font28);
            fm42 = getFontMetrics(font42);
        }

        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;

            // get the dimension from the size of the panel
            Dimension d = getSize();
            g2.setBackground(black);
            g2.clearRect(0,0,d.width, d.height);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(jfcBlue);

            if (currentName == null) {
                currentName = "";
            }
            g2.setFont(font24);
            g2.drawString(currentName, 5, fm28.getHeight() - 5);

            if (duration > 0.0) {
                double seconds = getSeconds();
                if (midiEOM || audioEOM) {
                        seconds = duration;
                }

                if (seconds > 0.0) {
                    g2.setFont(font42);
                    String s = String.valueOf(seconds);

                    s = s.substring(0, s.indexOf('.') + 2);
                    int strW = (int) fm42.getStringBounds(s, g2).getWidth();
                    g2.drawString(s, d.width-strW-9, fm42.getAscent());

                    int num = 30;
                    Font font15 = new Font("Monospaced", Font.ITALIC, 15);
                    g2.setFont(font24);

                    AudioFormat f = ((Clip) currentSound).getFormat();
                    String format;
                    String channels;
                    String sampleRate;
                    String sampleSizeInBits;
                    if (f.getChannels() == 1) {
                        channels = "Mono";
                    } else if (f.getChannels() == 2) {
                        channels = "Stereo";
                    }
                    else {channels = "";}
                    sampleRate = Float.toString(f.getSampleRate()) + " Hz";
                    sampleSizeInBits = Integer.toString(f.getSampleSizeInBits());

                    format = channels + ", " + sampleRate + ", " + sampleSizeInBits + "-bit";

                    int hh = (int) (d.height * 0.25);
                    int ww = ((int) (d.width) / (int) num);
                    g2.drawString(format, ww, d.height-hh);
                }
            }
        }

        public void start() {
        	pbThread = new Thread(this);
        	pbThread.setName("PlaybackMonitor");
        	pbThread.start();
        }

        public void stop() {
            if (pbThread != null) {
                pbThread.interrupt();
            }
            pbThread = null;
        }

        public void run() {
            while (pbThread != null) {
                try {
                    pbThread.sleep(99);
                }
                catch (Exception e) {break;}

                repaint();
            }

            // The media rendering is done, set back to null
            pbThread = null;
        }

    }


    class JukeTable extends JPanel implements ActionListener {

        TableModel dataModel;

        public JukeTable() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(260, 300));

            final String[] names = {"#", "Name"};

            dataModel = new AbstractTableModel() {
                public int getColumnCount() {return names.length;}
                public int getRowCount() {return sounds.size();}

                public Object getValueAt(int row, int col) {
                        if (col == 0) {
                                return new Integer(row);
                        }
                        else if (col == 1) {
                                Object object = sounds.get(row);
                                if (object instanceof File) {
                                        return ((File) object).getName();
                                }
                        }
                        return null;
                }

                public String getColumnName(int col) {return names[col];}
                public Class getColumnClass(int c) {return getValueAt(0,c).getClass();}
                public boolean isCellEditable(int row, int col) {return false;}
                public void setValueAt(Object aValue, int row, int col) {}
            };

            table = new JTable(dataModel);
            TableColumn col = table.getColumn("#");
            col.setMaxWidth(20);
            table.sizeColumnsToFit(20);

            JScrollPane scrollPane = new JScrollPane(table);
            EmptyBorder eb = new EmptyBorder(5,5,2,5);
            scrollPane.setBorder(new CompoundBorder(eb,new EtchedBorder()));
            add(scrollPane);

            JPanel pl = new JPanel();
            JMenuBar menuBar = new JMenuBar();
            menuBar.setBorder(new BevelBorder(BevelBorder.RAISED));
            JMenu menu = (JMenu) menuBar.add(new JMenu("Remove"));
            JMenuItem item = menu.add(new JMenuItem("Selected"));
            item.addActionListener(this);
            item = menu.add(new JMenuItem("All"));
            item.addActionListener(this);

            loopB = new JButton("loop");
            loopB.setBackground(Color.gray);
            loopB.addActionListener(this);
            pl.add(loopB);
            loopB.setSelected(true);

            add("South", pl);

        }

        public void actionPerformed(ActionEvent e) {

            Object object = e.getSource();

            if (object instanceof JMenuItem) {
                    JMenuItem menuItem = (JMenuItem) object;

                    if (menuItem.getText().equals("Selected")) {
                            int rows[] = table.getSelectedRows();
                            Vector tmp = new Vector();
                            for (int i = 0; i < rows.length; i++) {
                                    tmp.add(sounds.get(rows[i]));
                            }
                            sounds.removeAll(tmp);
                            tableChanged();
                    } else if (menuItem.getText().equals("All")) {
                            sounds.clear();
                            tableChanged();
                    }

            } else if (object instanceof JButton) {
                    JButton button = (JButton) object;

                    if (button.getText().equals("loop")) {
                            loopB.setSelected(!loopB.isSelected());
                            loopB.setBackground(loopB.isSelected() ? Color.gray : Color.lightGray);
                    }
                    startB.setEnabled(sounds.size() != 0);

            }
        }

        public void tableChanged() {
                table.tableChanged(new TableModelEvent(dataModel));
        }

    }

    class Loading extends Thread {
            double extent;
            int incr;

            public void run() {
                    extent = 360.0; incr =  10;
                    while (true) {

                            try {sleep(99);} catch (Exception ex) {break;}
                            playbackMonitor.repaint();
                    }
            }
            // Do not need to implement this method
            public void render(Dimension d, Graphics2D g2) {}

    }

    public static void main(String args[]) {
        
        final JavaPlayer player = new JavaPlayer();
        player.open();
        JFrame f = new JFrame("Juke Box");
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {System.exit(0);}
            public void windowIconified(WindowEvent e) {}
        });
        
        f.getContentPane().add("Center", player);
        f.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int w = 750;
        int h = 340;
        f.setLocation(screenSize.width/2 - w/2, screenSize.height/2 - h/2);
        f.setSize(w, h);
        f.setVisible(true);

    }
}


