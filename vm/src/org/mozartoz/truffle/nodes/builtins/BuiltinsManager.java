package org.mozartoz.truffle.nodes.builtins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozartoz.truffle.Options;
import org.mozartoz.truffle.nodes.DerefIfBoundNode;
import org.mozartoz.truffle.nodes.DerefNode;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.OzRootNode;
import org.mozartoz.truffle.nodes.call.ReadArgumentNode;
import org.mozartoz.truffle.nodes.local.BindNodeGen;
import org.mozartoz.truffle.runtime.OzLanguage;
import org.mozartoz.truffle.runtime.OzProc;
import org.mozartoz.truffle.runtime.OzRecord;
import org.mozartoz.truffle.translator.BuiltinsRegistry;
import org.mozartoz.truffle.translator.Loader;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class BuiltinsManager {

	private static final String[] MODULES = {
			// Base.oz
			"Value",
			"Literal",
			"Cell",
			"Port",
			"Atom",
			"Name",
			"Int",
			"Float",
			"Number",
			"Tuple",
			"Procedure",
			"Dictionary",
			"Record",
			"Chunk",
			"VirtualString",
			"VirtualByteString",
			"Coders",
			"Array",
			"Object",
			"Thread",
			"Exception",
			"Time",
			"ForeignPointer",
			"CompactString",
			"System",
			"Property",
			"WeakReference",
			// Init.oz
			"Boot",
			"OS",
			"Debug",
			"Reflection",
			"Space",
			"Browser",
	};

	private static final Map<String, OzProc> BUILTINS = new HashMap<>();
	private static final Map<String, DynamicObject> BOOT_MODULES = new HashMap<>();

	private static final Source BUILTINS_SOURCE = Loader.buildInternalSource("builtin");
	private static final SourceSection BUILTINS_SOURCE_SECTION = BUILTINS_SOURCE.createUnavailableSection();

	private static SourceSection builtinSourceSection(String builtinName) {
		if (Options.PROFILER) {
			return Loader.buildInternalSource(builtinName).createUnavailableSection();
		} else {
			return BUILTINS_SOURCE_SECTION;
		}
	}

	public static OzProc getBuiltin(String moduleName, String builtinName) {
		return getBuiltin(moduleName + "." + builtinName);
	}

	public static OzProc getBuiltin(String name) {
		OzProc fun = BUILTINS.get(name);
		if (fun == null) {
			throw new Error("No builtin " + name);
		}
		return fun;
	}

	public static DynamicObject getBootModule(String name) {
		assert BOOT_MODULES.containsKey(name) : name;
		return BOOT_MODULES.get(name);
	}

	public static DynamicObject getBootModulesRecord() {
		return OzRecord.buildRecord("bootModules", BOOT_MODULES);
	}

	public static void defineBuiltins(OzLanguage language) {
		assert BUILTINS.isEmpty();
		String pkg = BuiltinsManager.class.getPackage().getName();
		for (String module : MODULES) {
			try {
				Class<?> moduleFactory = Class.forName(pkg + "." + module + "BuiltinsFactory");
				Object untypedFactories = moduleFactory.getMethod("getFactories").invoke(null);
				@SuppressWarnings("unchecked")
				List<NodeFactory<? extends OzNode>> factories = (List<NodeFactory<? extends OzNode>>) untypedFactories;
				installBuiltins(language, module, factories);
			} catch (ReflectiveOperationException e) {
				throw new Error(e);
			}
		}
		BOOT_MODULES.put("Boot_WeakRef", BOOT_MODULES.get("Boot_WeakReference")); // TODO: hack
	}

	public static OzNode createNodeFromFactory(NodeFactory<? extends OzNode> factory, OzNode[] args) {
		Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
		if (builtin == null) {
			builtin = Builtin.DEFAULT;
		}

		Object[] arguments = new OzNode[args.length];
		for (int i = 0; i < args.length; i++) {
			arguments[i] = transformArgumentNode(builtin, i, args[i]);
		}

		return factory.createNode(arguments);
	}

	private static void installBuiltins(OzLanguage language, String module, List<NodeFactory<? extends OzNode>> factories) {
		// The builtins of this module only, indexed by the builtin name
		Map<String, OzProc> builtins = new HashMap<>(factories.size());

		for (NodeFactory<? extends OzNode> factory : factories) {
			Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
			if (builtin == null) {
				builtin = Builtin.DEFAULT;
			}

			int arity = factory.getNodeSignatures().get(0).size();
			OzNode[] readArguments = new OzNode[arity];
			for (int i = 0; i < readArguments.length; i++) {
				readArguments[i] = new ReadArgumentNode(i);
			}
			OzNode node = createNodeFromFactory(factory, readArguments);

			if (!builtin.proc()) {
				node = BindNodeGen.create(new ReadArgumentNode(arity), node);
				arity++;
			}

			final String builtinName;
			if (!builtin.name().isEmpty()) {
				builtinName = builtin.name();
			} else {
				String nodeName = factory.getNodeClass().getSimpleName();
				builtinName = Character.toLowerCase(nodeName.charAt(0)) + nodeName.substring(1, nodeName.lastIndexOf("Node"));
			}
			String name = module + "." + builtinName;

			SourceSection sourceSection = builtinSourceSection(builtinName);
			OzRootNode rootNode = new OzRootNode(language, sourceSection, name, new FrameDescriptor(), node, arity, true);
			OzProc function = new OzProc(rootNode.toCallTarget(), null, arity);

			assert !BUILTINS.containsKey(name) : name;
			BUILTINS.put(name, function);
			builtins.put(builtinName.intern(), function);
			BuiltinsRegistry.register(module, builtinName, arity);
		}

		String label = module.toLowerCase().intern();
		BOOT_MODULES.put(("Boot_" + module).intern(), OzRecord.buildRecord(label, builtins));
	}

	private static OzNode transformArgumentNode(Builtin builtin, int i, OzNode arg) {
		if (annoArrayInclude(builtin.deref(), i + 1)) {
			return DerefNode.create(arg);
		} else if (annoArrayInclude(builtin.tryDeref(), i + 1)) {
			return DerefIfBoundNode.create(arg);
		} else {
			return arg;
		}
	}

	private static boolean annoArrayInclude(int[] array, int i) {
		for (int e : array) {
			if (e == Builtin.ALL || e == i) {
				return true;
			}
		}
		return false;
	}

}
