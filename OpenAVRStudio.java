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

    // --- DEVELOPER INFO (EDIT THIS) ---
    private static final String DEV_NAME = "isg32";
    private static final String DEV_GITHUB = "https://github.com/isg32";

    // --- CONFIG & STATE ---
    private File currentFile = null;
    private boolean isRunning = false;
    private Thread simThread;

    // --- GUI COMPONENTS ---
    private JTextPane codeEditor; 
    private DefaultStyledDocument doc;
    private JTextArea consoleOutput;
    
    // Debugger Views
    private JTable registerTable;
    private DefaultTableModel registerModel;
    private JTable memoryTable;
    private DefaultTableModel memoryModel;
    private JLabel pcLabel, cycleLabel;
    private JLabel statusLabel;
    
    // I/O Bit Visualizer
    private JPanel ioPanel;
    private Map<String, JCheckBox[]> ioBits = new HashMap<>();

    // Toolbar
    private JComboBox<String> deviceCombo;
    private JComboBox<String> freqCombo;
    private JButton btnRun; 

    // Undo/Redo
    private UndoManager undoManager;

    public OpenAVRStudio() {
        super("OpenAVR Studio"); // Clean Title
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1250, 850);
        setLayout(new BorderLayout());

        setupMenuBar();
        setupToolbar();

        // --- MAIN LAYOUT ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.5); 

        // 1. LEFT: CODE EDITOR
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

        // Syntax Highlight Trigger
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
        
        // Sync Line Numbers
        codeEditor.getDocument().addDocumentListener(new DocumentListener(){
            public String getText(){
                int caretPosition = codeEditor.getDocument().getLength();
                Element root = codeEditor.getDocument().getDefaultRootElement();
                String text = "1" + System.getProperty("line.separator");
                for(int i = 2; i < root.getElementIndex( caretPosition ) + 2; i++){
                    text += i + System.getProperty("line.separator");
                }
                return text;
            }
            @Override public void changedUpdate(DocumentEvent de) { lines.setText(getText()); }
            @Override public void insertUpdate(DocumentEvent de) { lines.setText(getText()); }
            @Override public void removeUpdate(DocumentEvent de) { lines.setText(getText()); }
        });
        
        editorPanel.add(editorScroll, BorderLayout.CENTER);
        
        // Default Code
        try { doc.insertString(0, getStarterCode(), null); } catch(Exception e){}
        
        // 2. RIGHT: SIMULATOR & MEMORY
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Top: Registers
        JPanel topSim = new JPanel(new GridLayout(1, 2));
        
        String[] regCols = {"Reg", "Val (Bin)", "Hex", "Dec"};
        registerModel = new DefaultTableModel(regCols, 0);
        for(int i=0; i<32; i++) {
            registerModel.addRow(new Object[]{"R"+i, "00000000", "0x00", "0"});
        }
        
        registerTable = new JTable(registerModel);
        registerTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        registerTable.getColumnModel().getColumn(1).setPreferredWidth(75);
        registerTable.getColumnModel().getColumn(2).setPreferredWidth(45);
        registerTable.getColumnModel().getColumn(3).setPreferredWidth(35);
        
        topSim.add(new JScrollPane(registerTable));
        
        // I/O Visuals
        ioPanel = new JPanel();
        ioPanel.setLayout(new BoxLayout(ioPanel, BoxLayout.Y_AXIS));
        addPortVisualizer("PORTA");
        addPortVisualizer("PORTB");
        addPortVisualizer("PORTC");
        addPortVisualizer("PORTD");
        topSim.add(new JScrollPane(ioPanel));
        
        rightPanel.add(topSim, BorderLayout.NORTH);
        
        // Center: Memory Map
        String[] memCols = {"Addr", "00", "01", "02", "03", "04", "05", "06", "07"};
        memoryModel = new DefaultTableModel(memCols, 0);
        for(int i=0; i<32; i++) {
            memoryModel.addRow(new Object[]{String.format("0x%04X", i*8), "00", "00", "00", "00", "00", "00", "00", "00"});
        }
        memoryTable = new JTable(memoryModel);
        rightPanel.add(new JScrollPane(memoryTable), BorderLayout.CENTER);

        // Bottom: Console
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
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        
        JPanel counters = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pcLabel = new JLabel("PC: 0x0000 ");
        cycleLabel = new JLabel("Cycles: 0 ");
        counters.add(pcLabel); counters.add(cycleLabel);
        
        bottomBar.add(statusLabel, BorderLayout.WEST);
        bottomBar.add(counters, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        setVisible(true);
    }

    // --- SETUP: MENU & TOOLBAR ---

    private void setupMenuBar() {
        JMenuBar mb = new JMenuBar();
        
        // 1. FILE MENU
        JMenu f = new JMenu("File");
        
        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openFileAction());
        
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> quickSaveAction());
        
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveAsAction());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        f.add(openItem);
        f.add(saveItem);
        f.add(saveAsItem);
        f.addSeparator();
        f.add(exitItem);
        
        // 2. HELP MENU (NEW)
        JMenu h = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About Developer");
        aboutItem.addActionListener(e -> showAboutDialog());
        h.add(aboutItem);
        
        mb.add(f);
        mb.add(h);
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

        tb.add(new JLabel(" Device: "));
        tb.add(deviceCombo);
        tb.add(new JLabel(" Clock: "));
        tb.add(freqCombo);
        tb.addSeparator();
        tb.add(btnRun);
        
        add(tb, BorderLayout.NORTH);
    }

    private void showAboutDialog() {
        String msg = "<html><body style='width: 250px; text-align: center; font-family: sans-serif;'>" +
                     "<h2 style='color: #2c3e50;'>OpenAVR Studio</h2>" +
                     "<p><b>Version 6.6</b></p>" +
                     "<hr>" +
                     "<p>Developed by:<br><b style='font-size: 14px; color: #e67e22;'>" + DEV_NAME + "</b></p>" +
                     "<p>GitHub:<br><span style='color: #3498db;'>" + DEV_GITHUB + "</span></p>" +
                     "<br></body></html>";
                     
        JOptionPane.showMessageDialog(this, msg, "About Developer", JOptionPane.INFORMATION_MESSAGE);
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
                Pattern pComment = Pattern.compile(";.*");

                Matcher m = pOpcode.matcher(text);
                while(m.find()) doc.setCharacterAttributes(m.start(), m.end()-m.start(), opcode, false);
                
                m = pComment.matcher(text);
                while(m.find()) doc.setCharacterAttributes(m.start(), m.end()-m.start(), comment, false);
            } catch (Exception e) {}
        });
    }

    // --- FILE I/O ---

    private void openFileAction() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("AVR Assembly (*.S, *.asm)", "S", "asm"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try {
                codeEditor.setText("");
                FileReader fr = new FileReader(currentFile);
                codeEditor.read(fr, null);
                fr.close();
                statusLabel.setText(" Opened: " + currentFile.getName());
                log("Loaded: " + currentFile.getAbsolutePath());
                undoManager.discardAllEdits();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        }
    }

    private void quickSaveAction() {
        if (currentFile != null) saveToCurrentFile();
        else saveAsAction();
    }

    private void saveAsAction() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("AVR Assembly (*.S)", "S"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".s")) {
                file = new File(file.getParentFile(), file.getName() + ".S");
            }
            currentFile = file;
            saveToCurrentFile();
        }
    }

    private void saveToCurrentFile() {
        if (currentFile == null) return;
        try (FileWriter fw = new FileWriter(currentFile)) {
            codeEditor.write(fw);
            statusLabel.setText(" Saved: " + currentFile.getName());
            log("Saved: " + currentFile.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
        }
    }

    // --- RUN LOGIC ---

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
        
        String processed = convertToGcc(codeEditor.getText());
        try {
            File f = new File("main.S");
            FileWriter fw = new FileWriter(f);
            fw.write(processed);
            fw.close();
        } catch(Exception e) { log("Disk Error: " + e); return; }

        String mcu = deviceCombo.getSelectedItem().toString().toLowerCase();
        if(!runCmd("avr-gcc", "-mmcu="+mcu, "-nostdlib", "-o", "main.elf", "main.S")) return;
        if(!runCmd("avr-objcopy", "-O", "ihex", "-R", ".eeprom", "main.elf", "main.hex")) return;
        
        log(">>> BUILD SUCCESSFUL.");
        log(">>> STARTING SIMULATION...");
        
        startSimulator("main.hex");
    }

    private String convertToGcc(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("#define __SFR_OFFSET 0\n#include <avr/io.h>\n.global main\nmain:\n");
        for(String line : code.split("\n")) {
            String t = line.trim().toUpperCase();
            if(t.startsWith(".INCLUDE")) sb.append("; ").append(line).append("\n");
            else sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private boolean runCmd(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while((l=r.readLine())!=null) log(l);
            return p.waitFor() == 0;
        } catch(Exception e) { log("Error: " + e); return false; }
    }

    private void log(String s) { consoleOutput.append(s + "\n"); consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength()); }

    // --- SIMULATOR ENGINE ---

    private int pc = 0;
    private int cycles = 0;
    private int[] flash;
    private int[] registers = new int[32];
    private int[] io = new int[64];

    private void startSimulator(String hexFile) {
        flash = loadHex(hexFile);
        if(flash == null) { log("❌ ERROR: Could not load HEX."); return; }
        
        pc = 0;
        cycles = 0;
        Arrays.fill(registers, 0);
        Arrays.fill(io, 0);
        isRunning = true;

        simThread = new Thread(() -> {
            log(">>> EXECUTION STARTED");
            try {
                while(isRunning) {
                    if(pc >= flash.length) { log(">>> END OF MEMORY."); break; }
                    
                    int opcode = flash[pc];
                    int nextPc = pc + 1;
                    boolean changed = false;

                    // LDI
                    if ((opcode & 0xF000) == 0xE000) { 
                        int K = ((opcode & 0xF00) >> 4) | (opcode & 0x0F);
                        int d = ((opcode & 0x00F0) >> 4) + 16;
                        registers[d] = K;
                        changed = true;
                    }
                    // SBI
                    else if ((opcode & 0xFF00) == 0x9A00) { 
                        int A = (opcode & 0x00F8) >> 3;
                        int b = (opcode & 7);
                        io[A] |= (1 << b);
                        updateIO(A);
                    }
                    // CBI
                    else if ((opcode & 0xFF00) == 0x9800) { 
                        int A = (opcode & 0x00F8) >> 3;
                        int b = (opcode & 7);
                        io[A] &= ~(1 << b);
                        updateIO(A);
                    }
                    // RJMP
                    else if ((opcode & 0xF000) == 0xC000) { 
                        int k = opcode & 0x0FFF;
                        if ((k & 0x800) != 0) k -= 4096;
                        nextPc = pc + 1 + k;
                    }

                    pc = nextPc;
                    cycles++;
                    
                    if(changed) updateGUI();
                    Thread.sleep(50); 
                }
            } catch (Exception e) { log("❌ CRASH: " + e.getMessage()); }
            
            SwingUtilities.invokeLater(() -> {
               if (isRunning) {
                   btnRun.setText("▶ RUN & EXECUTE");
                   btnRun.setBackground(new Color(0, 120, 60));
                   isRunning = false;
               }
            });
        });
        simThread.start();
    }

    private void stopSimulation() {
        isRunning = false;
        try { simThread.join(200); } catch(Exception e){}
        log(">>> EXECUTION STOPPED.");
    }

    private void updateIO(int addr) {
        String port = null;
        if(addr == 0x1B) port = "PORTA";
        else if(addr == 0x18) port = "PORTB";
        else if(addr == 0x15) port = "PORTC";
        else if(addr == 0x12) port = "PORTD";
        
        if(port != null) {
            final String pName = port;
            final int val = io[addr];
            SwingUtilities.invokeLater(() -> {
                JCheckBox[] bits = ioBits.get(pName);
                if(bits != null) {
                    for(int i=0; i<8; i++) bits[i].setSelected((val & (1<<i)) != 0);
                }
            });
        }
    }

    private void updateGUI() {
        SwingUtilities.invokeLater(() -> {
            for(int i=0; i<32; i++) {
                int val = registers[i];
                String bin = String.format("%8s", Integer.toBinaryString(val)).replace(' ', '0');
                String hex = String.format("0x%02X", val);
                String dec = String.valueOf(val);

                if(!registerModel.getValueAt(i, 2).equals(hex)) {
                    registerModel.setValueAt(bin, i, 1);
                    registerModel.setValueAt(hex, i, 2);
                    registerModel.setValueAt(dec, i, 3);
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
                int type=Integer.parseInt(l.substring(7,9),16);
                if(type==0) for(int i=0;i<len;i+=2) {
                   int lo=Integer.parseInt(l.substring(9+i*2,11+i*2),16);
                   int hi=Integer.parseInt(l.substring(11+i*2,13+i*2),16);
                   m[addr+(i/2)]=(hi<<8)|lo;
                }
            }
            return m;
        } catch(Exception e){ return null; }
    }

    private String getStarterCode() {
        return "; ATmega32 Blink Demo\n.INCLUDE \"M32DEF.INC\"\n\nLDI R16, 0xFF\nOUT DDRB, R16   ; Set Port B Output\n\nLOOP:\n  SBI PORTB, 0  ; LED ON\n  RCALL DELAY\n  CBI PORTB, 0  ; LED OFF\n  RCALL DELAY\n  RJMP LOOP\n\nDELAY:\n  LDI R20, 10\nL1: DEC R20\n  BRNE L1\n  RET";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(OpenAVRStudio::new);
    }
}
