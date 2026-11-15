import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.*;
import java.util.concurrent.ExecutionException;

/* ---------- Data access ---------- */
interface FeeRepository {
    void save(String regNo, double amount) throws SQLException;
}

class JdbcFeeRepository implements FeeRepository {          // real database version (needs MySQL driver)
    private final String url, user, pwd;
    JdbcFeeRepository(String url, String user, String pwd) { this.url = url; this.user = user; this.pwd = pwd; }
    public void save(String regNo, double amount) throws SQLException {
        String sql = "INSERT INTO fees(reg_no, amount) VALUES (?, ?)";   // PreparedStatement: no SQL injection
        try (Connection c = DriverManager.getConnection(url, user, pwd);
             PreparedStatement ps = c.prepareStatement(sql)) {            // try-with-resources: always closed
            ps.setString(1, regNo);
            ps.setDouble(2, amount);
            ps.executeUpdate();
        }
    }
}

/* ---------- Application logic (no Swing code) ---------- */
class FeeService {
    static class InvalidInputException extends Exception {
        InvalidInputException(String m) { super(m); }
    }
    private final FeeRepository repository;
    private final long delayMs;              // simulated bank confirmation
    private String lastReceipt;

    FeeService(FeeRepository repository, long delayMs) { this.repository = repository; this.delayMs = delayMs; }

    double validate(String regNo, String amountText) throws InvalidInputException {
        if (regNo == null || regNo.trim().isEmpty()) throw new InvalidInputException("Register number is required.");
        if (!regNo.trim().matches("[A-Za-z0-9]{6,15}")) throw new InvalidInputException("Register number must be 6-15 letters/digits.");
        if (amountText == null || amountText.trim().isEmpty()) throw new InvalidInputException("Amount is required.");
        double a;
        try { a = Double.parseDouble(amountText.trim()); }
        catch (NumberFormatException e) { throw new InvalidInputException("Amount must be a number."); }
        if (Double.isNaN(a) || Double.isInfinite(a) || a <= 0) throw new InvalidInputException("Amount must be greater than zero.");
        if (a > 1_000_000) throw new InvalidInputException("Amount cannot exceed 1000000.");
        return a;
    }

    String pay(String regNo, String amountText) throws InvalidInputException, SQLException {
        double a = validate(regNo, amountText);
        try { if (delayMs > 0) Thread.sleep(delayMs); }          // runs on a worker thread, never the EDT
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("Interrupted", e); }
        repository.save(regNo.trim(), a);
        lastReceipt = String.format("RECEIPT: %s paid %.2f", regNo.trim(), a);
        return "Payment successful. " + lastReceipt;
    }

    String receipt() throws InvalidInputException {
        if (lastReceipt == null) throw new InvalidInputException("No payment has been made yet.");
        return lastReceipt;
    }
}

/* ---------- View: only collects input and shows results ---------- */
public class FeeWindow extends JFrame {
    private final JTextField regNo = new JTextField(12);
    private final JTextField amount = new JTextField(10);
    private final JButton pay = new JButton("Pay"), clear = new JButton("Clear"), receipt = new JButton("Receipt");
    private final JLabel status = new JLabel(" ");
    private final FeeService service;
    private boolean paying;

    FeeWindow(FeeService service) {
        super("Student Fee Payment");
        this.service = service;
        JPanel form = new JPanel(new GridLayout(2, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        form.add(new JLabel("Register No:")); form.add(regNo);
        form.add(new JLabel("Amount:"));      form.add(amount);
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(pay); buttons.add(clear); buttons.add(receipt);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        pay.addActionListener(e -> onPay());          // one small listener per button
        clear.addActionListener(e -> onClear());
        receipt.addActionListener(e -> onReceipt());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (paying && JOptionPane.showConfirmDialog(FeeWindow.this,
                        "A payment is in progress. Close anyway?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(null);
    }

    private void onPay() {
        final String r = regNo.getText(), a = amount.getText();
        try { service.validate(r, a); }
        catch (FeeService.InvalidInputException ex) { show(ex.getMessage(), true); return; }
        paying = true;
        pay.setEnabled(false);                         // stops double payment
        show("Processing payment...", false);
        new SwingWorker<String, Void>() {              // slow work off the event thread
            @Override protected String doInBackground() throws Exception { return service.pay(r, a); }
            @Override protected void done() {
                paying = false;
                pay.setEnabled(true);
                try { show(get(), false); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                catch (ExecutionException ex) {
                    Throwable c = ex.getCause();
                    show(c instanceof SQLException ? "Database error: payment was not saved." : c.getMessage(), true);
                }
            }
        }.execute();
    }

    private void onClear() { regNo.setText(""); amount.setText(""); show(" ", false); }

    private void onReceipt() {
        try { JOptionPane.showMessageDialog(this, service.receipt(), "Receipt", JOptionPane.INFORMATION_MESSAGE); }
        catch (FeeService.InvalidInputException ex) { show(ex.getMessage(), true); }
    }

    private void show(String msg, boolean error) {
        status.setForeground(error ? Color.RED.darker() : new Color(0, 110, 0));
        status.setText(msg);
    }

    /* java FeeWindow        -> opens the window (in-memory repository, no database needed)
       java FeeWindow test   -> runs the logic tests, no window */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("test")) { runTests(); return; }
        // To use MySQL instead: new JdbcFeeRepository("jdbc:mysql://localhost:3306/college", "root", "password")
        FeeRepository repo = (reg, amt) -> System.out.println("Saved " + reg + " : " + amt);
        FeeService service = new FeeService(repo, 3000);
        SwingUtilities.invokeLater(() -> new FeeWindow(service).setVisible(true));
    }

    static int passed = 0, failed = 0;
    static void check(String name, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + name); if (ok) passed++; else failed++; }
    static boolean rejects(FeeService s, String r, String a) {
        try { s.validate(r, a); return false; } catch (FeeService.InvalidInputException e) { return true; }
    }
    static void runTests() throws Exception {
        java.util.List<String> saved = new java.util.ArrayList<>();
        FeeService s = new FeeService((r, a) -> saved.add(r + ":" + a), 0);
        check("empty register number rejected", rejects(s, "", "100"));
        check("short register number rejected", rejects(s, "ab1", "100"));
        check("SQL-injection style input rejected", rejects(s, "x'); DROP TABLE fees;--", "100"));
        check("empty amount rejected", rejects(s, "CSA09001", ""));
        check("non-numeric amount rejected", rejects(s, "CSA09001", "abc"));
        check("negative amount rejected", rejects(s, "CSA09001", "-5"));
        check("zero amount rejected", rejects(s, "CSA09001", "0"));
        check("NaN amount rejected", rejects(s, "CSA09001", "NaN"));
        check("huge amount rejected", rejects(s, "CSA09001", "99999999"));
        check("nothing saved for invalid input", saved.isEmpty());
        boolean none = false;
        try { s.receipt(); } catch (FeeService.InvalidInputException e) { none = true; }
        check("receipt before payment refused", none);
        String m = s.pay("CSA09001", "2500.50");
        check("valid payment saved once", saved.size() == 1);
        check("confirmation returned", m.startsWith("Payment successful"));
        check("receipt available after payment", s.receipt().contains("CSA09001"));
        System.out.println(passed + " passed, " + failed + " failed");
    }
}
