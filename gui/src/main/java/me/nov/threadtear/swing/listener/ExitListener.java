package me.nov.threadtear.swing.listener;

import me.nov.threadtear.Threadtear;

import java.awt.event.*;

import javax.swing.*;

public class ExitListener extends WindowAdapter {
  private JFrame frame;

  public ExitListener(JFrame frame) {
    this.frame = frame;
  }

  @Override
  public void windowClosing(WindowEvent we) {
    if (Threadtear.getInstance().listPanel.classList.inputFile == null ||
      JOptionPane
            .showConfirmDialog(frame, "Do you really want to exit?", "Confirm", JOptionPane.YES_NO_OPTION) ==
            JOptionPane.YES_OPTION) {
      Runtime.getRuntime().exit(0);
    }
  }
}
