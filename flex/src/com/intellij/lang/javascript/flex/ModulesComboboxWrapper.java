package com.intellij.lang.javascript.flex;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventListener;

public class ModulesComboboxWrapper {
  public interface Listener extends EventListener {
    void moduleChanged();
  }

  private final JComboBox myComboBox;
  protected final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  public ModulesComboboxWrapper(JComboBox comboBox) {
    myComboBox = comboBox;

    myComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FlexUtils.removeIncorrectItemFromComboBoxIfPresent(myComboBox, Module.class);
        myDispatcher.getMulticaster().moduleChanged();
      }
    });

    myComboBox.setRenderer(new CellRenderer(myComboBox.getRenderer(), true));
  }

  public void configure(Project project, final @Nullable String selectedModuleName) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    Module selectedModule;
    if (StringUtil.isEmpty(selectedModuleName)) { // select any Flex module or module with Flex facet
      selectedModule = modules.length > 0 ? modules[0] : null;
      for (final Module module : modules) {
        if (FlexUtils.isFlexModuleOrContainsFlexFacet(module)) {
          selectedModule = module;
          break;
        }
      }
    }
    else {
      selectedModule = moduleManager.findModuleByName(selectedModuleName);
    }

    if (selectedModule != null && ArrayUtil.contains(selectedModule, modules)) {
      myComboBox.setModel(new DefaultComboBoxModel(modules));
      myComboBox.setSelectedItem(selectedModule);
    }
    else {
      final Object[] modulesEx = new Object[modules.length + 1];
      modulesEx[0] = selectedModuleName;
      System.arraycopy(modules, 0, modulesEx, 1, modules.length);
      myComboBox.setModel(new DefaultComboBoxModel(modulesEx));
    }
  }

  @Nullable
  public Module getSelectedModule() {
    final Object selectedItem = myComboBox.getSelectedItem();
    if (selectedItem instanceof Module && FlexUtils.isFlexModuleOrContainsFlexFacet((Module)selectedItem)) {
      return (Module)selectedItem;
    }
    else {
      return null;
    }
  }

  @NotNull
  public String getSelectedText() {
    final Object selectedItem = myComboBox.getSelectedItem();
    if (selectedItem instanceof Module) {
      return ((Module)selectedItem).getName();
    }
    else {
      return selectedItem != null ? selectedItem.toString() : "";
    }
  }

  public void addActionListener(Listener listener) {
    myDispatcher.addListener(listener);
  }

  public static class CellRenderer extends ListCellRendererWrapper {
    private final boolean myShowErrorIcon;

    public CellRenderer(ListCellRenderer original, boolean showErrorIcon) {
      super(original);
      myShowErrorIcon = showErrorIcon;
    }

    @Override
    public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof Module) {
        final Module module = (Module)value;
        setText(module.getName());
        setIcon(ModuleType.get(module).getNodeIcon(false));
      }
      else if (myShowErrorIcon) {
        setIcon(PlatformIcons.ERROR_INTRODUCTION_ICON);
      }
    }
  }
}
