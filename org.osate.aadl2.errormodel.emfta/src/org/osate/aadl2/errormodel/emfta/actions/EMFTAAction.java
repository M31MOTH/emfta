/*
 * <copyright>
 * Copyright  2012 by Carnegie Mellon University, all rights reserved.
 *
 * Use of the Open Source AADL Tool Environment (OSATE) is subject to the terms of the license set forth
 * at http://www.eclipse.org/org/documents/epl-v10.html.
 *
 * NO WARRANTY
 *
 * ANY INFORMATION, MATERIALS, SERVICES, INTELLECTUAL PROPERTY OR OTHER PROPERTY OR RIGHTS GRANTED OR PROVIDED BY
 * CARNEGIE MELLON UNIVERSITY PURSUANT TO THIS LICENSE (HEREINAFTER THE "DELIVERABLES") ARE ON AN "AS-IS" BASIS.
 * CARNEGIE MELLON UNIVERSITY MAKES NO WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED AS TO ANY MATTER INCLUDING,
 * BUT NOT LIMITED TO, WARRANTY OF FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABILITY, INFORMATIONAL CONTENT,
 * NONINFRINGEMENT, OR ERROR-FREE OPERATION. CARNEGIE MELLON UNIVERSITY SHALL NOT BE LIABLE FOR INDIRECT, SPECIAL OR
 * CONSEQUENTIAL DAMAGES, SUCH AS LOSS OF PROFITS OR INABILITY TO USE SAID INTELLECTUAL PROPERTY, UNDER THIS LICENSE,
 * REGARDLESS OF WHETHER SUCH PARTY WAS AWARE OF THE POSSIBILITY OF SUCH DAMAGES. LICENSEE AGREES THAT IT WILL NOT
 * MAKE ANY WARRANTY ON BEHALF OF CARNEGIE MELLON UNIVERSITY, EXPRESS OR IMPLIED, TO ANY PERSON CONCERNING THE
 * APPLICATION OF OR THE RESULTS TO BE OBTAINED WITH THE DELIVERABLES UNDER THIS LICENSE.
 *
 * Licensee hereby agrees to defend, indemnify, and hold harmless Carnegie Mellon University, its trustees, officers,
 * employees, and agents from all claims or demands made against them (and any related losses, expenses, or
 * attorney's fees) arising out of, or relating to Licensee's and/or its sub licensees' negligent use or willful
 * misuse of or negligent conduct or willful misconduct regarding the Software, facilities, or other rights or
 * assistance granted by Carnegie Mellon University under this License, including, but not limited to, any claims of
 * product liability, personal injury, death, damage to property, or violation of any laws or regulations.
 *
 * Carnegie Mellon Carnegie Mellon University Software Engineering Institute authored documents are sponsored by the U.S. Department
 * of Defense under Contract F19628-00-C-0003. Carnegie Mellon University retains copyrights in all material produced
 * under this contract. The U.S. Government retains a non-exclusive, royalty-free license to publish or reproduce these
 * documents, or allow others to do so, for U.S. Government purposes only pursuant to the copyright license
 * under the contract clause at 252.227.7013.
 * </copyright>
 */
package org.osate.aadl2.errormodel.emfta.actions;

import java.io.FileOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.xtext.ui.util.ResourceUtil;
import org.osate.aadl2.Element;
import org.osate.aadl2.errormodel.emfta.fta.EventWrapper;
import org.osate.aadl2.instance.InstanceObject;
import org.osate.aadl2.instance.SystemInstance;
import org.osate.aadl2.util.OsateDebug;
import org.osate.ui.actions.AaxlReadOnlyActionAsJob;
import org.osate.ui.dialogs.Dialog;
import org.osate.xtext.aadl2.errormodel.errorModel.ErrorBehaviorState;
import org.osate.xtext.aadl2.errormodel.errorModel.ErrorTypes;
import org.osate.xtext.aadl2.errormodel.errorModel.TypeToken;
import org.osate.xtext.aadl2.errormodel.util.EMV2Util;

public final class EMFTAAction extends AaxlReadOnlyActionAsJob {

	private static String ERROR_STATE_NAME = null;

	private org.osate.aadl2.errormodel.analysis.fta.Event ftaEvent;

	@Override
	protected String getMarkerType() {
		return "org.osate.analysis.errormodel.FaultImpactMarker";
	}

	@Override
	protected String getActionName() {
		return "FTA";
	}

	@Override
	public void doAaxlAction(IProgressMonitor monitor, Element obj) {
		SystemInstance si;

		monitor.beginTask("Fault Tree Analysis", IProgressMonitor.UNKNOWN);

		si = null;

		if (obj instanceof InstanceObject) {
			si = ((InstanceObject) obj).getSystemInstance();
		}

		if (si == null) {
			Dialog.showInfo("Fault Tree Analysis", "Please choose an instance model");
			monitor.done();
		}

		if (!EMV2Util.hasCompositeErrorBehavior(si)) {
			Dialog.showInfo("Fault Tree Analysis", "Your system instance does not have a composite error behavior");
			monitor.done();
		}

		final Display d = PlatformUI.getWorkbench().getDisplay();
		d.syncExec(new Runnable() {

			@Override
			public void run() {
				IWorkbenchWindow window;
				Shell sh;

				window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				sh = window.getShell();

				InputDialog fd = new InputDialog(
						sh,
						"Error State name",
						"Please specify the name of the error state name\n(with the optional error type separated by a space)",
						"failed", null);
				if (fd.open() == Window.OK) {
					ERROR_STATE_NAME = fd.getValue();
				} else {
					ERROR_STATE_NAME = null;
				}

			}
		});

		if (ERROR_STATE_NAME != null) {
			String errorStateName;
			String errorStateTypeName;
			ErrorBehaviorState errorState;
			ErrorTypes errorType;

			/**
			 * Init variables and environment
			 */
			errorStateName = null;
			errorStateTypeName = null;
			errorState = null;
			errorType = null;

			org.osate.aadl2.errormodel.analysis.fta.FTAUtils.init(si);

			if (ERROR_STATE_NAME.contains(" ")) {
				errorStateName = ERROR_STATE_NAME.substring(0, ERROR_STATE_NAME.indexOf(" "));
				errorStateTypeName = ERROR_STATE_NAME.substring(ERROR_STATE_NAME.indexOf(" "),
						ERROR_STATE_NAME.length());
			} else {
				errorStateName = ERROR_STATE_NAME;
			}

			OsateDebug
					.osateDebug("[EMFTAAction] error state=" + errorStateName + "|related type=" + errorStateTypeName);

			for (ErrorBehaviorState ebs : EMV2Util.getAllErrorBehaviorStates(si)) {
				if (ebs.getName().equalsIgnoreCase(errorStateName)) {
					errorState = ebs;

					if (errorStateTypeName != null) {
						for (TypeToken tt : ebs.getTypeSet().getTypeTokens()) {
							for (ErrorTypes et : tt.getType()) {
								if (et.getName().equalsIgnoreCase(errorStateTypeName)) {
									errorType = et;
								}
							}
						}
					}
				}
			}

			ftaEvent = null;

			if (errorState != null) {
				ftaEvent = org.osate.aadl2.errormodel.analysis.fta.FTAUtils
						.processErrorState(si, errorState, errorType);
			}

			if (ftaEvent != null) {

				serializeEmftaModel(EventWrapper.toEmftaModel(ftaEvent), ResourceUtil.getFile(si.eResource())
						.getProject(), "newfta.emfta");

			} else {
				Dialog.showInfo("Fault Tree Analysis",
						"Unable to create the Fault Tree Analysis, please read the help content");
			}
		}

		monitor.done();
	}

	public static void serializeEmftaModel(edu.cmu.emfta.FTAModel emftaModel, IProject activeProject, String filename) {

		OsateDebug.osateDebug("[Utils]", "serializeReqSpecModel activeProject=" + activeProject);

		IFile newFile = activeProject.getFile(filename);

		try {

			ResourceSet set = new ResourceSetImpl();
			Resource res = set.createResource(URI.createURI(activeProject.getFile("newfta.emfta").toString()));

			res.getContents().add(emftaModel);

			FileOutputStream fos = new FileOutputStream(newFile.getRawLocation().toFile());
			res.save(fos, null);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}