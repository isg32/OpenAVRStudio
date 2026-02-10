import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class OpenAVRStudio extends JFrame {

    // --- DEVELOPER INFO ---
    private static final String DEV_NAME = "isg32";
    private static final String DEV_GITHUB = "https://github.com/isg32";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    // --- CONFIG & STATE ---
    private File currentFile = null;
    private boolean isRunning = false;
    private Thread simThread;

    // --- GUI COMPONENTS ---
    private JTextPane codeEditor; 
    private DefaultStyledDocument doc;
    private JTextArea consoleOutput;
    private JTable registerTable;
    private DefaultTableModel registerModel;
    private JTable memoryTable;
    private DefaultTableModel memoryModel;
    private JLabel pcLabel, cycleLabel;
    private JLabel statusLabel;
    private JPanel ioPanel;
    private Map<String, JCheckBox[]> ioBits = new HashMap<>();
    private JComboBox<String> deviceCombo;
    private JComboBox<String> freqCombo;
    private JButton btnRun; 
    private UndoManager undoManager;

    public OpenAVRStudio() {
        super("OpenAVR Studio");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1250, 850);
        setLayout(new BorderLayout());

        setupMenuBar();
        setupToolbar();

        // --- MAIN LAYOUT ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.5); 

        // LEFT: CODE EDITOR
        JPanel editorPanel = new JPanel(new BorderLayout());
        doc = new DefaultStyledDocument();
        codeEditor = new JTextPane(doc);
        codeEditor.setFont(new Font("Monospaced", Font.PLAIN, 14));
        codeEditor.setCaretColor(Color.WHITE);
        codeEditor.setBackground(new Color(30, 30, 30));
        codeEditor.setForeground(new Color(220, 220, 220));
        
        undoManager = new UndoManager();
        doc.addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));
        setupEditorKeyBindings();

        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { highlightSyntax(); }
            public void removeUpdate(DocumentEvent e) { highlightSyntax(); }
            public void changedUpdate(DocumentEvent e) {}
        });

        // Line Numbers
        JTextArea lines = new JTextArea("1");
        lines.setBackground(new Color(50, 50, 50));
        lines.setForeground(Color.GRAY);
        lines.setEditable(false);
        lines.setFont(new Font("Monospaced", Font.PLAIN, 14));
        lines.setMargin(new Insets(0, 5, 0, 5));
        
        JScrollPane editorScroll = new JScrollPane(codeEditor);
        editorScroll.setRowHeaderView(lines);
        
        codeEditor.getDocument().addDocumentListener(new DocumentListener(){
            public String getText(){
                int caretPosition = codeEditor.getDocument().getLength();
                Element root = codeEditor.getDocument().getDefaultRootElement();
                StringBuilder text = new StringBuilder("1" + System.lineSeparator());
                for(int i = 2; i < root.getElementIndex( caretPosition ) + 2; i++){
                    text.append(i).append(System.lineSeparator());
                }
                return text.toString();
            }
            @Override public void changedUpdate(DocumentEvent de) { lines.setText(getText()); }
            @Override public void insertUpdate(DocumentEvent de) { lines.setText(getText()); }
            @Override public void removeUpdate(DocumentEvent de) { lines.setText(getText()); }
        });
        
        editorPanel.add(editorScroll, BorderLayout.CENTER);
        try { doc.insertString(0, getStarterCode(), null); } catch(Exception e){}
        
        // RIGHT: SIMULATOR & MEMORY
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel topSim = new JPanel(new GridLayout(1, 2));
        
        String[] regCols = {"Reg", "Val (Bin)", "Hex", "Dec"};
        registerModel = new DefaultTableModel(regCols, 0);
        for(int i=0; i<32; i++) registerModel.addRow(new Object[]{"R"+i, "00000000", "0x00", "0"});
        
        registerTable = new JTable(registerModel);
        topSim.add(new JScrollPane(registerTable));
        
        ioPanel = new JPanel();
        ioPanel.setLayout(new BoxLayout(ioPanel, BoxLayout.Y_AXIS));
        addPortVisualizer("PORTA"); addPortVisualizer("PORTB");
        addPortVisualizer("PORTC"); addPortVisualizer("PORTD");
        topSim.add(new JScrollPane(ioPanel));
        
        rightPanel.add(topSim, BorderLayout.NORTH);
        
        String[] memCols = {"Addr", "00", "01", "02", "03", "04", "05", "06", "07"};
        memoryModel = new DefaultTableModel(memCols, 0);
        for(int i=0; i<32; i++) memoryModel.addRow(new Object[]{String.format("0x%04X", i*8), "00", "00", "00", "00", "00", "00", "00", "00"});
        memoryTable = new JTable(memoryModel);
        rightPanel.add(new JScrollPane(memoryTable), BorderLayout.CENTER);

        consoleOutput = new JTextArea();
        consoleOutput.setRows(8);
        consoleOutput.setBackground(Color.BLACK);
        consoleOutput.setForeground(Color.GREEN);
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rightPanel.add(new JScrollPane(consoleOutput), BorderLayout.SOUTH);

        mainSplit.setLeftComponent(editorPanel);
        mainSplit.setRightComponent(rightPanel);
        add(mainSplit, BorderLayout.CENTER);

        // Status Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        statusLabel = new JLabel(" New File");
        JPanel counters = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pcLabel = new JLabel("PC: 0x0000 ");
        cycleLabel = new JLabel("Cycles: 0 ");
        counters.add(pcLabel); counters.add(cycleLabel);
        bottomBar.add(statusLabel, BorderLayout.WEST);
        bottomBar.add(counters, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void setupMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu f = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openFileAction());
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> quickSaveAction());
        f.add(openItem); f.add(saveItem);
        
        JMenu h = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About Developer");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this, "OpenAVR Studio\nDev: " + DEV_NAME + "\n" + DEV_GITHUB));
        h.add(aboutItem);
        
        mb.add(f); mb.add(h);
        setJMenuBar(mb);
    }

    private void setupToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        deviceCombo = new JComboBox<>(new String[]{"ATmega32", "ATmega328P"});
        freqCombo = new JComboBox<>(new String[]{"16 MHz", "8 MHz"});
        btnRun = new JButton("▶ RUN & EXECUTE");
        btnRun.setBackground(new Color(0, 120, 60));
        btnRun.setForeground(Color.WHITE);
        btnRun.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRun.addActionListener(e -> toggleRun());
        tb.add(new JLabel(" Device: ")); tb.add(deviceCombo);
        tb.add(new JLabel(" Clock: ")); tb.add(freqCombo);
        tb.addSeparator(); tb.add(btnRun);
        add(tb, BorderLayout.NORTH);
    }

    private void setupEditorKeyBindings() {
        codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "Undo");
        codeEditor.getActionMap().put("Undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if(undoManager.canUndo()) undoManager.undo(); }
        });
        codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "Redo");
        codeEditor.getActionMap().put("Redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if(undoManager.canRedo()) undoManager.redo(); }
        });
    }

    private void addPortVisualizer(String name) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder(name));
        JCheckBox[] bits = new JCheckBox[8];
        for(int i=7; i>=0; i--) {
            bits[i] = new JCheckBox(String.valueOf(i));
            bits[i].setEnabled(false);
            p.add(bits[i]);
        }
        ioBits.put(name, bits);
        ioPanel.add(p);
    }

    private void highlightSyntax() {
        SwingUtilities.invokeLater(() -> {
            try {
                String text = doc.getText(0, doc.getLength());
                StyleContext sc = StyleContext.getDefaultStyleContext();
                AttributeSet normal = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(220, 220, 220));
                AttributeSet opcode = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(86, 156, 214));
                AttributeSet comment = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, new Color(87, 166, 74));
                doc.setCharacterAttributes(0, text.length(), normal, true);
                
                Pattern pOpcode = Pattern.compile("\\b(LDI|MOV|ADD|SUB|ANDI|ORI|RJMP|JMP|CALL|RET|SBI|CBI|SWAP|INC|DEC|BREQ|BRNE|OUT|IN)\\b", Pattern.CASE_INSENSITIVE);
                Matcher m = pOpcode.matcher(text);
                while(m.find()) doc.setCharacterAttributes(m.start(), m.end()-m.start(), opcode, false);
                
                m = Pattern.compile(";.*").matcher(text);
                while(m.find()) doc.setCharacterAttributes(m.start(), m.end()-m.start(), comment, false);
            } catch (Exception e) {}
        });
    }

    private void openFileAction() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("AVR Assembly (*.S, *.asm)", "S", "asm"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try (FileReader fr = new FileReader(currentFile)) {
                codeEditor.setText("");
                codeEditor.read(fr, null);
                statusLabel.setText(" Opened: " + currentFile.getName());
                undoManager.discardAllEdits();
            } catch (IOException ex) { log("Error: " + ex.getMessage()); }
        }
    }

    private void quickSaveAction() {
        if (currentFile == null) {
            JFileChooser chooser = new JFileChooser(new File("."));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                currentFile = chooser.getSelectedFile();
                if (!currentFile.getName().contains(".")) currentFile = new File(currentFile.getParent(), currentFile.getName()+".S");
            }
        }
        if (currentFile != null) {
            try (FileWriter fw = new FileWriter(currentFile)) {
                codeEditor.write(fw);
                statusLabel.setText(" Saved: " + currentFile.getName());
            } catch (IOException ex) { log("Error saving: " + ex.getMessage()); }
        }
    }

    private void toggleRun() {
        if (!isRunning) {
            startBuildAndRun();
            btnRun.setText("⏹ STOP EXECUTION");
            btnRun.setBackground(new Color(180, 50, 50));
        } else {
            stopSimulation();
            btnRun.setText("▶ RUN & EXECUTE");
            btnRun.setBackground(new Color(0, 120, 60));
        }
    }

    private void startBuildAndRun() {
        consoleOutput.setText("");
        log(">>> BUILDING...");
        
        // 1. Determine executable paths (Bundled vs System)
        String appPath = System.getProperty("user.dir");
        String avrPath = appPath + File.separator + "avr-bin" + File.separator + "bin" + File.separator;
        
        String gccExec = IS_WINDOWS ? "avr-gcc.exe" : "avr-gcc";
        String objExec = IS_WINDOWS ? "avr-objcopy.exe" : "avr-objcopy";

        // If bundled folder exists, use it. Otherwise, assume it's in System PATH.
        String gccPath = new File(avrPath + gccExec).exists() ? avrPath + gccExec : gccExec;
        String objPath = new File(avrPath + objExec).exists() ? avrPath + objExec : objExec;

        try {
            File f = new File("main.S");
            try (FileWriter fw = new FileWriter(f)) { fw.write(convertToGcc(codeEditor.getText())); }
        } catch(Exception e) { log("Disk Error: " + e); return; }

        String mcu = deviceCombo.getSelectedItem().toString().toLowerCase();
        if(!runCmd(gccPath, "-mmcu="+mcu, "-nostdlib", "-o", "main.elf", "main.S")) return;
        if(!runCmd(objPath, "-O", "ihex", "-R", ".eeprom", "main.elf", "main.hex")) return;
        
        log(">>> BUILD SUCCESSFUL.");
        startSimulator("main.hex");
    }

    private String convertToGcc(String code) {
        return "#define __SFR_OFFSET 0\n#include <avr/io.h>\n.global main\nmain:\n" + code;
    }

    private boolean runCmd(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l; while((l=r.readLine())!=null) log(l);
            return p.waitFor() == 0;
        } catch(Exception e) { 
            log("Error: " + e.getMessage() + "\nCheck if avr-gcc is installed or bundled."); 
            return false; 
        }
    }

    private void log(String s) { 
        consoleOutput.append(s + "\n"); 
        consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
    }

    // --- SIMULATOR ENGINE ---
    private int pc = 0, cycles = 0;
    private int[] flash, registers = new int[32], io = new int[64];

    private void startSimulator(String hexFile) {
        flash = loadHex(hexFile);
        if(flash == null) { log("❌ ERROR: Could not load HEX."); return; }
        pc = 0; cycles = 0; Arrays.fill(registers, 0); Arrays.fill(io, 0);
        isRunning = true;
        simThread = new Thread(() -> {
            log(">>> EXECUTION STARTED");
            try {
                while(isRunning && pc < flash.length) {
                    int opcode = flash[pc];
                    int nextPc = pc + 1;
                    boolean changed = false;

                    if ((opcode & 0xF000) == 0xE000) { // LDI
                        int K = ((opcode & 0xF00) >> 4) | (opcode & 0x0F);
                        registers[((opcode & 0x00F0) >> 4) + 16] = K;
                        changed = true;
                    } else if ((opcode & 0xFF00) == 0x9A00) { // SBI
                        int A = (opcode & 0x00F8) >> 3;
                        io[A] |= (1 << (opcode & 7)); updateIO(A);
                    } else if ((opcode & 0xFF00) == 0x9800) { // CBI
                        int A = (opcode & 0x00F8) >> 3;
                        io[A] &= ~(1 << (opcode & 7)); updateIO(A);
                    } else if ((opcode & 0xF000) == 0xC000) { // RJMP
                        int k = opcode & 0x0FFF; if ((k & 0x800) != 0) k -= 4096;
                        nextPc = pc + 1 + k;
                    }

                    pc = nextPc; cycles++;
                    if(changed || cycles % 10 == 0) updateGUI();
                    Thread.sleep(50);
                }
            } catch (Exception e) { log("❌ CRASH: " + e.getMessage()); }
            SwingUtilities.invokeLater(() -> {
                btnRun.setText("▶ RUN & EXECUTE");
                btnRun.setBackground(new Color(0, 120, 60));
                isRunning = false;
            });
        });
        simThread.start();
    }

    private void stopSimulation() { isRunning = false; }

    private void updateIO(int addr) {
        String port = (addr==0x1B)?"PORTA":(addr==0x18)?"PORTB":(addr==0x15)?"PORTC":(addr==0x12)?"PORTD":null;
        if(port != null) {
            int val = io[addr];
            SwingUtilities.invokeLater(() -> {
                JCheckBox[] bits = ioBits.get(port);
                if(bits != null) for(int i=0; i<8; i++) bits[i].setSelected((val & (1<<i)) != 0);
            });
        }
    }

    private void updateGUI() {
        SwingUtilities.invokeLater(() -> {
            for(int i=0; i<32; i++) {
                String hex = String.format("0x%02X", registers[i]);
                if(!registerModel.getValueAt(i, 2).equals(hex)) {
                    registerModel.setValueAt(String.format("%8s", Integer.toBinaryString(registers[i])).replace(' ', '0'), i, 1);
                    registerModel.setValueAt(hex, i, 2);
                    registerModel.setValueAt(String.valueOf(registers[i]), i, 3);
                }
            }
            pcLabel.setText(String.format("PC: 0x%04X ", pc*2));
            cycleLabel.setText("Cycles: " + cycles + " ");
        });
    }

    private int[] loadHex(String f) {
        int[] m = new int[32768];
        try(BufferedReader b=new BufferedReader(new FileReader(f))){
            String l;
            while((l=b.readLine())!=null){
                if(!l.startsWith(":")) continue;
                int len=Integer.parseInt(l.substring(1,3),16);
                int addr=Integer.parseInt(l.substring(3,7),16)/2;
                if(Integer.parseInt(l.substring(7,9),16)==0) for(int i=0;i<len;i+=2) {
                   int lo=Integer.parseInt(l.substring(9+i*2,11+i*2),16);
                   int hi=Integer.parseInt(l.substring(11+i*2,13+i*2),16);
                   m[addr+(i/2)]=(hi<<8)|lo;
                }
            }
            return m;
        } catch(Exception e){ return null; }
    }

    private String getStarterCode() {
        return "; ATmega32 Blink Demo\nLDI R16, 0xFF\nOUT DDRB, R16\n\nLOOP:\n  SBI PORTB, 0  ; LED ON\n  CBI PORTB, 0  ; LED OFF\n  RJMP LOOP";
    }

    public static void main(String[] args) { 
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception e){}
        SwingUtilities.invokeLater(OpenAVRStudio::new); 
    }
}
