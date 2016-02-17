package org.mozartoz.truffle.nodes.builtins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.OzRootNode;
import org.mozartoz.truffle.nodes.call.ReadArgumentNode;
import org.mozartoz.truffle.nodes.local.BindNodeGen;
import org.mozartoz.truffle.runtime.OzFunction;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.SourceSection;

public abstract class BuiltinsManager {

	private static final Map<String, OzFunction> BUILTINS = new HashMap<>();

	private static final String[] MODULES = { "Value", "Int", "Number", "Record", "Exception" };

	public static OzFunction getBuiltin(String moduleName, String builtinName) {
		return BUILTINS.get(moduleName + "." + builtinName);
	}

	public static void defineBuiltins() {
		assert BUILTINS.isEmpty();
		String pkg = BuiltinsManager.class.getPackage().getName();
		for (String module : MODULES) {
			try {
				Class<?> moduleFactory = Class.forName(pkg + "." + module + "BuiltinsFactory");
				Object untypedFactories = moduleFactory.getMethod("getFactories").invoke(null);
				@SuppressWarnings("unchecked")
				List<NodeFactory<? extends OzNode>> factories = (List<NodeFactory<? extends OzNode>>) untypedFactories;
				installBuiltins(module, factories);
			} catch (ReflectiveOperationException e) {
				throw new Error(e);
			}
		}
	}

	private static void installBuiltins(String module, List<NodeFactory<? extends OzNode>> factories) {
		for (NodeFactory<? extends OzNode> factory : factories) {
			Builtin builtinAnno = factory.getNodeClass().getAnnotation(Builtin.class);
			boolean anno = builtinAnno != null;
			final String builtinName;
			if (anno && !builtinAnno.name().isEmpty()) {
				builtinName = builtinAnno.name();
			} else {
				String nodeName = factory.getNodeClass().getSimpleName();
				builtinName = Character.toLowerCase(nodeName.charAt(0)) + nodeName.substring(1, nodeName.lastIndexOf("Node"));
			}
			String name = module + "." + builtinName;
			SourceSection sourceSection = SourceSection.createUnavailable("builtin", name);

			int arity = factory.getNodeSignatures().get(0).size();
			Object[] readArguments = new OzNode[arity];
			for (int i = 0; i < readArguments.length; i++) {
				readArguments[i] = new ReadArgumentNode(i);
			}
			OzNode node = factory.createNode(readArguments);
			if (!(anno && builtinAnno.proc())) {
				node = BindNodeGen.create(null, new ReadArgumentNode(arity), node);
			}

			OzRootNode rootNode = new OzRootNode(sourceSection, new FrameDescriptor(), node);
			CallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
			OzFunction function = new OzFunction(callTarget, null);
			BUILTINS.put(name, function);
		}
	}
}