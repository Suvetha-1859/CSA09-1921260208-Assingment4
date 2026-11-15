import javax.swing.*;
import java.awt.FlowLayout;
import java.awt.event.*;

public class ConverterStyles {
    public static void main(String[] args) {
        String s = args.length > 0 ? args[0].toLowerCase() : "d";
        SwingUtilities.invokeLater(() -> {
            JFrame f = s.equals("a") ? new ConverterA() : s.equals("b") ? new ConverterB()
                     : s.equals("c") ? new ConverterC() : new ConverterD();
            f.setVisible(true);
        });
    }
}

/** Shared conversion logic. Rates are per 1 INR. */
final class Rates {
    public static final String[] CURRENCIES = {"INR", "USD", "EUR"};
    private Rates() { }

    private static double perInr(String c) {
        switch (c) {
            case "USD": return 0.012;
            case "EUR": return 0.011;
            default:    return 1.0;
        }
    }

    public static String convertText(String amountText, String from, String to) {
        try {
            double a = Double.parseDouble(amountText.trim());
            if (Double.isNaN(a) || Double.isInfinite(a) || a < 0) return "Invalid amount";
            return String.format("%.2f %s", a / perInr(from) * perInr(to), to);
        } catch (NumberFormatException ex) {
            return "Invalid amount";
        }
    }
}

/** Style (a): the frame itself implements ActionListener. */
class ConverterA extends JFrame implements ActionListener {
    private final JTextField amount = new JTextField(8);
    private final JComboBox<String> from = new JComboBox<>(Rates.CURRENCIES);
    private final JComboBox<String> to = new JComboBox<>(new String[]{"USD", "EUR", "INR"});
    private final JButton convertButton = new JButton("Convert");
    private final JLabel result = new JLabel(" ");

    public ConverterA() {
        super("Converter (a) frame implements listener");
        setLayout(new FlowLayout());
        add(new JLabel("Amount")); add(amount); add(from); add(new JLabel("to")); add(to);
        add(convertButton); add(result);
        convertButton.addActionListener(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); System.exit(0); }
        });
        pack();
    }

    @Override public void actionPerformed(ActionEvent e) {
        String text = Rates.convertText(amount.getText(), (String) from.getSelectedItem(), (String) to.getSelectedItem());
        result.setText(text);
    }
}

/** Style (b): a named inner class. */
class ConverterB extends JFrame {
    private final JTextField amount = new JTextField(8);
    private final JComboBox<String> from = new JComboBox<>(Rates.CURRENCIES);
    private final JComboBox<String> to = new JComboBox<>(new String[]{"USD", "EUR", "INR"});
    private final JButton convertButton = new JButton("Convert");
    private final JLabel result = new JLabel(" ");

    public ConverterB() {
        super("Converter (b) named inner class");
        setLayout(new FlowLayout());
        add(new JLabel("Amount")); add(amount); add(from); add(new JLabel("to")); add(to);
        add(convertButton); add(result);
        convertButton.addActionListener(new ConvertListener());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); System.exit(0); }
        });
        pack();
    }

    private class ConvertListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            String text = Rates.convertText(amount.getText(), (String) from.getSelectedItem(), (String) to.getSelectedItem());
            result.setText(text);
        }
    }
}

/** Style (c): an anonymous inner class. */
class ConverterC extends JFrame {
    private final JTextField amount = new JTextField(8);
    private final JComboBox<String> from = new JComboBox<>(Rates.CURRENCIES);
    private final JComboBox<String> to = new JComboBox<>(new String[]{"USD", "EUR", "INR"});
    private final JButton convertButton = new JButton("Convert");
    private final JLabel result = new JLabel(" ");

    public ConverterC() {
        super("Converter (c) anonymous class");
        setLayout(new FlowLayout());
        add(new JLabel("Amount")); add(amount); add(from); add(new JLabel("to")); add(to);
        add(convertButton); add(result);
        convertButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                String text = Rates.convertText(amount.getText(), (String) from.getSelectedItem(), (String) to.getSelectedItem());
                result.setText(text);
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); System.exit(0); }
        });
        pack();
    }
}

/** Style (d): a lambda expression. */
class ConverterD extends JFrame {
    private final JTextField amount = new JTextField(8);
    private final JComboBox<String> from = new JComboBox<>(Rates.CURRENCIES);
    private final JComboBox<String> to = new JComboBox<>(new String[]{"USD", "EUR", "INR"});
    private final JButton convertButton = new JButton("Convert");
    private final JLabel result = new JLabel(" ");

    public ConverterD() {
        super("Converter (d) lambda");
        setLayout(new FlowLayout());
        add(new JLabel("Amount")); add(amount); add(from); add(new JLabel("to")); add(to);
        add(convertButton); add(result);
        convertButton.addActionListener(e -> {
            String text = Rates.convertText(amount.getText(), (String) from.getSelectedItem(), (String) to.getSelectedItem());
            result.setText(text);
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); System.exit(0); }
        });
        pack();
    }
}
