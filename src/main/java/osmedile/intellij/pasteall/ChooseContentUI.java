package osmedile.intellij.pasteall;

import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Class copied from ContentChooser.
 *
 * @author Olivier Smedile
 * @version $Id$
 */
public class ChooseContentUI extends JDialog {
    private static final Icon TEXT_ICON = IconLoader.getIcon("/fileTypes/text.png");


    private JPanel contentPane;

    /**
     * Paste button
     */
    private JButton pasteBt;

    /**
     *
     */
    private JButton cancelButton;

    /**
     * List of items that can be pasted
     */
    private JList pasteableList;

    private JRadioButton recentFirst;
    private JRadioButton olderFirst;
    private JSplitPane splitPane;

    /**
     * Filter the list of items
     */
    private JTextField pasteableFilter;

    /**
     * Preview of the selected item
     */
    private Editor selectedItemViewer;

    private Project project;
    private Editor editor;

    /**
     * Contains all items that can be pasted
     */
    private ArrayList<String> pasteables;


    private ChooseContentUI.MyListModel model;

    /**
     * Used to select a template
     */
    private JComboBox templateBox;

    /**
     *
     */
    private JPanel templatePanel;

    /**
     * Editor to preview and edit selected template.
     */
    private Editor templateViewer;

    private List<TemplateImpl> templateList;

    /**
     * True if template is used when pasting.
     */
    private JCheckBox useTemplatChk;
    private ChoosePasteAllAction callback;

// -------------------------- INNER CLASSES --------------------------

    /**
     * Filter value of pasterable items.
     * Cut text if there is more than 1 line and add "..."
     */
    private static class PasteableListCellRenderer extends ColoredListCellRenderer {
        protected void customizeCellRenderer(JList list, Object value,
                                             int index, boolean selected,
                                             boolean hasFocus) {
            setIcon(TEXT_ICON);
            if (index <= 9) {
                append(String.valueOf((index + 1) % 10) + "  ",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            String s = (String) value;
            int newLineIdx = s.indexOf('\n');
            if (newLineIdx == -1) {
                append(s.trim(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else {
                append(s.substring(0, newLineIdx).trim() + " ...",
                        SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }

    public class MyListModel extends AbstractListModel {
        public int getSize() {
            return pasteables.size();
        }

        public Object getElementAt(int idx) {
            //This can happen if pasteables is updated
            if (idx >= pasteables.size()) {
                return "";
            }
            return pasteables.get(idx);
        }

        public void fireChanges() {
            fireContentsChanged(pasteables, 0, pasteables.size());
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public ChooseContentUI() {
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(pasteBt);

        pasteBt.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        ActionListener cancelListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        };

        cancelButton.addActionListener(cancelListener);

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(cancelListener,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        //More specific
        pasteables = new ArrayList<String>();
        pasteableFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                rebuildListContent();
            }
        });

        model = new MyListModel();
        pasteableList.setModel(model);


        pasteableList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.isConsumed() || e.getClickCount() != 2 || e.isPopupTrigger()) {
                    return;
                }
                onOK();
            }
        });
        pasteableList.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
//                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
//                    int selectedIndex = pasteableList.getSelectedIndex();
//                    int size = pasteables.size();
//                    removeContentAt(pasteables.get(selectedIndex));
//                    rebuildListContent();
//                    if (size == 1) {
//                        onCancel();
//                        return;
//                    }
//                    pasteableList.setSelectedIndex(Math.min(selectedIndex, pasteables.size() - 1));
//                } else
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onOK();
                } else {
                    final char aChar = e.getKeyChar();
                    if (aChar >= '0' && aChar <= '9') {
                        int idx = aChar == '0' ? 9 : aChar - '1';
                        if (idx < pasteables.size()) {
                            pasteableList.setSelectedIndex(idx);
                        }
                    }
                }
            }
        });

        pasteableList.setCellRenderer(new PasteableListCellRenderer());
        pasteableList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                updateViewerForSelection();
                if (pasteableList.getSelectedIndices().length > 0) {
                    pasteBt.setEnabled(true);
                } else {
                    pasteBt.setEnabled(false);
                }
            }
        }
        );

        //Viewer
        selectedItemViewer = EditorFactory.getInstance().createViewer(
                EditorFactory.getInstance().createDocument(""), project);
        selectedItemViewer.getComponent().setPreferredSize(new Dimension(300, 150));
        selectedItemViewer.getSettings().setFoldingOutlineShown(false);
        selectedItemViewer.getSettings().setLineNumbersShown(true);
        selectedItemViewer.getSettings().setLineMarkerAreaShown(false);

        splitPane.setRightComponent(selectedItemViewer.getComponent());
        splitPane.setDividerLocation(0.8);
        splitPane.revalidate();

        //templates
        templateViewer = EditorFactory.getInstance().createEditor(
                EditorFactory.getInstance().createDocument(""), project);
        templateViewer.getComponent().setPreferredSize(new Dimension(300, 300));
        templateViewer.getSettings().setFoldingOutlineShown(false);
        templateViewer.getSettings().setLineNumbersShown(true);
        templateViewer.getSettings().setLineMarkerAreaShown(false);


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        templatePanel.add(templateViewer.getComponent(), gbc);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(olderFirst);
        buttonGroup.add(recentFirst);

        templateBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                TemplateImpl template = templateList.get(templateBox.getSelectedIndex());
                templateViewer.getDocument().setText(template.getString());
                TemplateEditorUtil.setHighlighter(
                        templateViewer, template.getTemplateContext());
            }
        });

        if (templateList.size() > 0) {
            templateBox.setSelectedIndex(0);
            templateViewer.getDocument().setText(templateList.get(0).getString());
            TemplateEditorUtil.setHighlighter(
                    templateViewer, templateList.get(0).getTemplateContext());
        }


        templateBox.setBackground(Color.WHITE);

        templateViewer.getDocument().addDocumentListener(new DocumentListener() {
            public void beforeDocumentChange(DocumentEvent event) {
            }

            public void documentChanged(DocumentEvent event) {
                if (!StringUtil.isEmpty(event.getNewFragment().toString()) &&
                        !useTemplatChk.isSelected()) {
                    useTemplatChk.setSelected(true);
                }
            }
        });

        rebuildListContent();

        pasteableList.requestFocus();
        pasteableList.requestFocusInWindow();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        contentPane.setPreferredSize(new Dimension(520, 570));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setMinimumSize(new Dimension(200, 150));
        panel1.setPreferredSize(new Dimension(350, 350));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(panel1, gbc);
        splitPane = new JSplitPane();
        splitPane.setOrientation(0);
        splitPane.setRequestFocusEnabled(true);
        splitPane.setResizeWeight(0.0);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(splitPane, gbc);
        pasteableList = new JList();
        pasteableList.setMinimumSize(new Dimension(200, 130));
        pasteableList.setPreferredSize(new Dimension(250, 350));
        splitPane.setLeftComponent(pasteableList);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 5, 0);
        contentPane.add(panel2, gbc);
        panel2.setBorder(BorderFactory.createTitledBorder("Filter"));
        pasteableFilter = new JTextField();
        pasteableFilter.setMinimumSize(new Dimension(50, 20));
        pasteableFilter.setText("");
        pasteableFilter.putClientProperty("html.disable", Boolean.TRUE);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(pasteableFilter, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        contentPane.add(panel3, gbc);
        recentFirst = new JRadioButton();
        recentFirst.setSelected(false);
        recentFirst.setText("Top to bottom (recent to older)");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(recentFirst, gbc);
        olderFirst = new JRadioButton();
        olderFirst.setSelected(true);
        olderFirst.setText("Bottom to top (older to recent)");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(olderFirst, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Paste order ...");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label1, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(spacer1, gbc);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.VERTICAL;
        contentPane.add(spacer2, gbc);
        templatePanel = new JPanel();
        templatePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(templatePanel, gbc);
        templatePanel.setBorder(BorderFactory.createTitledBorder("Template"));
        templateBox.setBackground(new Color(-1));
        templateBox.setEditable(false);
        templateBox.putClientProperty("html.disable", Boolean.TRUE);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        templatePanel.add(templateBox, gbc);
        useTemplatChk = new JCheckBox();
        useTemplatChk.setText("Use template");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        templatePanel.add(useTemplatChk, gbc);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        contentPane.add(panel4, gbc);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel4.add(panel5, gbc);
        pasteBt = new JButton();
        pasteBt.setText("OK");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel5.add(pasteBt, gbc);
        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel5.add(cancelButton, gbc);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(olderFirst);
        buttonGroup.add(recentFirst);
    }

    private void createUIComponents() {
        templateList = TemplateUtils.getSelectionTemplates();
        templateBox = new JComboBox(getTemplateDesc(templateList));
    }

    public static String[] getTemplateDesc(List<TemplateImpl> templates) {
        String[] tplIds = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            tplIds[i] = templates.get(i).getKey() + " " + templates.get(i).getDescription();
        }

        return tplIds;
    }

    private void onCancel() {
        dispose();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (selectedItemViewer != null) {
            EditorFactory.getInstance().releaseEditor(selectedItemViewer);
            selectedItemViewer = null;
        }
        if (templateViewer != null) {
            EditorFactory.getInstance().releaseEditor(templateViewer);
            templateViewer = null;
        }
    }

    private void onOK() {
        final String template;
        if (useTemplatChk.isSelected()) {
            template = templateViewer.getDocument().getText();
        } else {
            template = null;
        }

        //save dialog configuration
        callback.setTemplate(templateViewer.getDocument().getText());
        callback.setTplIdx(templateBox.getSelectedIndex());
        callback.setUseTemplate(useTemplatChk.isSelected());
        callback.setOlderFirst(olderFirst.isSelected());


        PasteUtils.pasteAll(editor, true, olderFirst.isSelected(), getSelectedValues(), template);

        dispose();
    }

    public String[] getSelectedValues() {
        final Object[] selected = pasteableList.getSelectedValues();
        String[] selects = new String[selected.length];
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(selected, 0, selects, 0, selected.length);

        return selects;
    }

    private void updateViewerForSelection() {
        if (pasteableList.getSelectedValue() == null) {
            selectedItemViewer.getDocument().setText("");
            return;
        }
        String fullString = (String) pasteableList.getSelectedValue();
        fullString = StringUtil.convertLineSeparators(fullString);

        selectedItemViewer.getDocument().setText(fullString);
    }

    private void rebuildListContent() {
        Transferable[] trans = CopyPasteManager.getInstance().getAllContents();

        pasteables.clear();
        for (Transferable tran : trans) {
            String value = PasteUtils.getValue(tran);
            if (StringUtil.isNotEmpty(value)) {
                if (!StringUtil.isEmptyOrSpaces(pasteableFilter.getText())) {
                    if (StringUtil.containsIgnoreCase(value, pasteableFilter.getText())) {
                        pasteables.add(value);
                    }
                } else {
                    pasteables.add(value);
                }
            }
        }

        model.fireChanges();

        //remove selection
        if (pasteables.size() > 0) {
            pasteableList.setSelectedIndex(0);
        } else {
            pasteableList.setSelectedIndex(-1);
            pasteBt.setEnabled(false);
        }
        updateViewerForSelection();
    }

    public ChooseContentUI(Project project, Editor editor, ChoosePasteAllAction callback) {
        this();
        this.project = project;
        this.editor = editor;
        this.callback = callback;

        int idx = callback.getTplIdx();
        if (idx >= 0 && idx < templateList.size()) {
            templateBox.setSelectedIndex(idx);
        }
        if (callback.getTemplate() != null) {
            templateViewer.getDocument().setText(callback.getTemplate());
        }
        useTemplatChk.setSelected(callback.getUseTemplate());

        if (callback.isOlderFirst()) {
            olderFirst.setSelected(true);
        } else {
            recentFirst.setSelected(true);
        }

    }

    // -------------------------- OTHER METHODS --------------------------

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
