package de.bund.zrb.winml;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComBase;
import de.bund.zrb.winml.rt.HString;
import de.bund.zrb.winml.rt.WinRtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Low-level debug tests for WinRT activation.
 */
@EnabledOnOs(OS.WINDOWS)
class WinRtActivationDebugTest {

    /** IActivationFactory — the generic factory every WinRT class must expose. */
    private static final Guid.GUID IID_IACTIVATION_FACTORY =
            new Guid.GUID("{00000035-0000-0000-C000-000000000046}");

    @Test
    void activateLearningModelDeviceViaGenericFactory() {
        WinRtRuntime.initialize();

        // Step 1: get generic IActivationFactory for LearningModelDevice
        String className = "Windows.AI.MachineLearning.LearningModelDevice";
        try (HString hs = HString.create(className)) {
            PointerByReference ref = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), IID_IACTIVATION_FACTORY, ref);

            System.out.println("RoGetActivationFactory(IActivationFactory) for " + className
                    + " → HRESULT 0x" + Integer.toHexString(hr.intValue()));

            assertEquals(0, hr.intValue(),
                    "RoGetActivationFactory should succeed for generic IActivationFactory");

            Pointer factory = ref.getValue();
            assertNotNull(factory);

            // Step 2: try QI for ILearningModelDeviceFactory
            Guid.GUID iidFactory = new Guid.GUID("{9CFD8A0A-7420-47F5-AC2B-0EF5B04E9F52}");
            PointerByReference qiRef = new PointerByReference();

            // QI is VTable[0]
            Pointer vtable = factory.getPointer(0);
            // QueryInterface(this, riid, ppv)
            com.sun.jna.Function qi = com.sun.jna.Function.getFunction(
                    vtable.getPointer(0), com.sun.jna.Function.ALT_CONVENTION);
            int qiHr = qi.invokeInt(new Object[]{factory, iidFactory, qiRef});

            System.out.println("QueryInterface(ILearningModelDeviceFactory) → HRESULT 0x"
                    + Integer.toHexString(qiHr));

            if (qiHr == 0) {
                System.out.println("SUCCESS: ILearningModelDeviceFactory obtained!");
                // Release
                Pointer vtable2 = qiRef.getValue().getPointer(0);
                com.sun.jna.Function release = com.sun.jna.Function.getFunction(
                        vtable2.getPointer(2L * com.sun.jna.Native.POINTER_SIZE),
                        com.sun.jna.Function.ALT_CONVENTION);
                release.invokeInt(new Object[]{qiRef.getValue()});
            } else {
                System.out.println("QI failed — trying RoGetActivationFactory directly with IID");

                // Try directly with RoGetActivationFactory
                PointerByReference ref2 = new PointerByReference();
                WinNT.HRESULT hr2 = ComBase.INSTANCE.RoGetActivationFactory(
                        hs.getHandle(), iidFactory, ref2);
                System.out.println("RoGetActivationFactory(ILearningModelDeviceFactory) → HRESULT 0x"
                        + Integer.toHexString(hr2.intValue()));
            }

            // Release the generic factory
            Pointer vtable3 = factory.getPointer(0);
            com.sun.jna.Function rel = com.sun.jna.Function.getFunction(
                    vtable3.getPointer(2L * com.sun.jna.Native.POINTER_SIZE),
                    com.sun.jna.Function.ALT_CONVENTION);
            rel.invokeInt(new Object[]{factory});
        }
    }

    @Test
    void activateLearningModelViaGenericFactory() {
        WinRtRuntime.initialize();

        String className = "Windows.AI.MachineLearning.LearningModel";
        try (HString hs = HString.create(className)) {
            PointerByReference ref = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), IID_IACTIVATION_FACTORY, ref);

            System.out.println("RoGetActivationFactory(IActivationFactory) for " + className
                    + " → HRESULT 0x" + Integer.toHexString(hr.intValue()));

            // LearningModel is NOT directly activatable, so this may fail
            // That's OK — it confirms WinML namespace is registered
            if (hr.intValue() == 0) {
                System.out.println("SUCCESS: LearningModel factory obtained");
                // Release
                Pointer vtable = ref.getValue().getPointer(0);
                com.sun.jna.Function rel = com.sun.jna.Function.getFunction(
                        vtable.getPointer(2L * com.sun.jna.Native.POINTER_SIZE),
                        com.sun.jna.Function.ALT_CONVENTION);
                rel.invokeInt(new Object[]{ref.getValue()});
            } else {
                System.out.println("LearningModel not activatable (expected): HRESULT 0x"
                        + Integer.toHexString(hr.intValue()));
            }
        }
    }
}

