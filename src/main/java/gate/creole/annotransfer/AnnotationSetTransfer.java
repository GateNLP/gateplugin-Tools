/*
 * AnnotationSetTransfer.java
 *
 * Copyright (c) 2009, The University of Sheffield.
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 *
 * Mark A. Greenwood, 7/10/2009
 */
package gate.creole.annotransfer;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.GateConstants;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import gate.gui.SerialControllerEditor;
import gate.swing.XJFileChooser;
import gate.util.BomStrippingInputStreamReader;
import gate.util.InvalidOffsetException;

/**
 * This plugin allows the names of annotations and features to be
 * changed as well as transfered from one annotation set to another.
 * Think of it as an extended version of the old AnnotationSet Transfer
 * plugin.
 * 
 * @author Mark A. Greenwood
 */
@CreoleResource(name = "Annotation Set Transfer", comment = "Annotation set transfer component.", helpURL = "http://gate.ac.uk/userguide/sec:misc-creole:ast")
public class AnnotationSetTransfer extends AbstractLanguageAnalyser implements ActionsPublisher {

  private static final long serialVersionUID = 3502991817151932971L;

  private String tagASName;

  private String outputASName, inputASName, textTagName;

  private Boolean copyAnnotations, transferAllUnlessFound;

  private gate.AnnotationSet bodyAnnotations = null;

  private List<String> annotationTypes = null;

  Map<String, Mapping> mappings = new HashMap<String, Mapping>();

  @Override
  public Resource init() throws ResourceInstantiationException {    
    return this;
  }

  @Override
  public void execute() throws ExecutionException {
    AnnotationSet inputAS = document.getAnnotations(inputASName);
    AnnotationSet outputAS = document.getAnnotations(outputASName);
    AnnotationSet tagAS = document.getAnnotations(tagASName);
    AnnotationSet annotsToTransfer = null;

    boolean newID = copyAnnotations && inputAS.equals(outputAS);

    mappings.clear();

    if(annotationTypes != null) {
      for(String type : annotationTypes) {
        String[] data = type.split("=", 2);
        String oldName = data[0].trim();
        String newName = data.length == 2 ? data[1].trim() : null;

        mappings.put(oldName, new Mapping(oldName, newName));
      }
    }
    // else
    // throw new
    // ExecutionException("The annotation list and URL cannot both be null");

    if(mappings.size() > 0) {
      annotsToTransfer = inputAS.get(mappings.keySet());
    }
    else {
      // transfer everything
      annotsToTransfer = inputAS.get();
    }
    // in case of no one annotation from some of annotationTypes
    if(annotsToTransfer == null || annotsToTransfer.size() == 0) return;
    // check if we have a BODY annotation
    // if not, just copy all
    if(textTagName == null || textTagName.equals("")) {
      // remove from input set unless we copy only
      if(!copyAnnotations) inputAS.removeAll(annotsToTransfer);
      transferAnnotations(new ArrayList<Annotation>(annotsToTransfer),
              outputAS, newID);

      return;
    }
    // get the BODY annotation
    bodyAnnotations = tagAS.get(textTagName);
    if(bodyAnnotations == null || bodyAnnotations.isEmpty()) {
      // outputAS.addAll(inputAS);
      if(transferAllUnlessFound) {
        // remove from input set unless we copy only
        if(!copyAnnotations) inputAS.removeAll(annotsToTransfer);
        transferAnnotations(new ArrayList<Annotation>(annotsToTransfer),
                outputAS, newID);
      }
      return;
    }
    List<Annotation> annots2Move = new ArrayList<Annotation>();
    Iterator<Annotation> bodyIter = bodyAnnotations.iterator();
    while(bodyIter.hasNext()) {
      Annotation bodyAnn = bodyIter.next();
      Long start = bodyAnn.getStartNode().getOffset();
      Long end = bodyAnn.getEndNode().getOffset();
      // get all annotations we want transferred
      AnnotationSet annots2Copy = annotsToTransfer.getContained(start, end);
      // copy them to the new set and delete them from the old one
      annots2Move.addAll(annots2Copy);
    }
    if(!copyAnnotations) inputAS.removeAll(annots2Move);
    transferAnnotations(annots2Move, outputAS, newID);
  }

  private void transferAnnotations(List<Annotation> toTransfer,
          AnnotationSet to, boolean newID) throws ExecutionException {
    for(Annotation annot : toTransfer) {
      Mapping m = mappings.get(annot.getType());

      String name = (m == null || m.newName == null
              ? annot.getType()
              : m.newName);

      try {
        FeatureMap params = Factory.newFeatureMap();
        params.putAll(annot.getFeatures());
        if(newID) {
          to.add(annot.getStartNode().getOffset(), annot.getEndNode()
                  .getOffset(), name, params);
        }
        else {
          to.add(annot.getId(), annot.getStartNode().getOffset(), annot
                  .getEndNode().getOffset(), name, params);
        }
      }
      catch(InvalidOffsetException e) {
        throw new ExecutionException(e);
      }
    }
  }

  @RunTime
  @Optional
  @CreoleParameter(comment="The name of the annotation set that contains textTagName", defaultValue = GateConstants.ORIGINAL_MARKUPS_ANNOT_SET_NAME)
  public void setTagASName(String newTagASName) {
    tagASName = newTagASName;
  }

  public String getTagASName() {
    return tagASName;
  }
  
  @RunTime
  @Optional
  @CreoleParameter(comment="The name of the annotation set used for input")
  public void setInputASName(String newInputASName) {
    inputASName = newInputASName;
  }

  public String getInputASName() {
    return inputASName;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment="The name of the annotation set used for output")
  public void setOutputASName(String newOutputASName) {
    outputASName = newOutputASName;
  }

  public String getOutputASName() {
    return outputASName;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment="The annotation type that contains the annotations to transfer")
  public void setTextTagName(String newTextTagName) {
    textTagName = newTextTagName;
  }

  public String getTextTagName() {
    return textTagName;
  }

  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The annotation types to transfer, leave blank for all types")
  public void setAnnotationTypes(List<String> newTypes) {
    annotationTypes = newTypes;
  }
  
  public Boolean getCopyAnnotations() {
    return this.copyAnnotations;
  }

  @RunTime
  @CreoleParameter(comment = "If true, the annotations will remain in the inputAS, otherwise they will be removed", defaultValue = "false")
  public void setCopyAnnotations(Boolean copyAnnotations) {
    this.copyAnnotations = copyAnnotations;
  }

  public Boolean getTransferAllUnlessFound() {
    return this.transferAllUnlessFound;
  }

  @RunTime
  @CreoleParameter(comment = "If true (default), all annotations are copied/moved if no annotation of the type specified for textTagName is found", defaultValue = "true")
  public void setTransferAllUnlessFound(Boolean value) {
    this.transferAllUnlessFound = value;
  }

  static class Mapping implements Serializable {
    
    private static final long serialVersionUID = 4708558248536631082L;
    
    String oldName, newName;

    // TODO implement the renaming of features as well as annotations
    // Map<String, String> features = new HashMap<String, String>();

    public Mapping(String oldName, String newName) {
      this.oldName = oldName;
      this.newName = newName;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append(oldName);
      if(newName != null) {
        result.append("=").append(newName);
      }
      return result.toString();
    }
  }

  @Override
  public List<Action> getActions() {

    Action action = new AbstractAction("Import Annotation Mapping...") {

      private static final long serialVersionUID = -9114664159904960511L;

      @Override
      public void actionPerformed(ActionEvent arg0) {
        
        XJFileChooser fileChooser = MainFrame.getFileChooser();
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setDialogTitle("Import Annotation Mapping...");
        //.showOpenDialog(MainFrame.getInstance());
        
        if (fileChooser.showOpenDialog(MainFrame.getInstance()) != XJFileChooser.APPROVE_OPTION) return;
        
        SerialControllerEditor.clearAllSelections();
        
        // TODO Auto-generated method stub
        try (BufferedReader in =
            new BomStrippingInputStreamReader(fileChooser.getSelectedFile().toURI().toURL().openStream())) {

          List<String> loaded = new ArrayList<String>();
          
          String line = in.readLine();
          while(line != null) {
            if(!line.trim().equals("")) {
              //String[] data = line.split("=", 2);
              //String oldName = data[0].trim();
              //String newName = data.length == 2 ? data[1].trim() : null;
              //mappings.put(oldName, new Mapping(oldName, newName));
              loaded.add(line.trim());
            }
            line = in.readLine();
          }
          
          setAnnotationTypes(loaded);
        } catch(IOException ioe) {
          ioe.printStackTrace();
        }
      }
    };
    
    return Collections.singletonList(action);    
  }
}
