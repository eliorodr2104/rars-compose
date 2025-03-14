import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import rars.*
import rars.api.Options
import rars.api.Program
import rars.riscv.InstructionSet
import rars.riscv.dump.DumpFormatLoader
import rars.riscv.hardware.*
import rars.simulator.Simulator
import rars.util.Binary
import rars.util.FilenameFinder
import rars.util.MemoryDump
import ui.Home
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintStream
import java.util.*
import kotlin.system.exitProcess


private var options: Options?  = null
private       var gui                = false
private       var simulate           = false
private       var rv64               = false
private       var displayFormat      = 0
private       var verbose            = false // display register name or address along with contents
private       var assembleProject    = false // assemble only the given file or all files in its directory
private       var countInstructions  = false // Whether to count and report number of instructions executed
private const val rangeSeparator     = "-"
private const val memoryWordsPerLine = 4 // display 4 memory words, tab separated, per line
private const val DECIMAL            = 0 // memory and register display format
private const val HEXADECIMAL        = 1 // memory and register display format
private const val ASCII              = 2 // memory and register display format
private       var registerDisplayList: ArrayList<String>? = null
private       var memoryDisplayList  : ArrayList<String>? = null
private       var filenameList       : ArrayList<String>? = null
private       var instructionCount   = 0
private       var out: PrintStream? = null // stream for display of command line output
private       var dumpTriples: ArrayList<Array<String>>?  = null // each element holds 3 arguments for dump option
private       var programArgumentList: ArrayList<String>? = null // optional program args for program (becomes argc, argv)
private       var assembleErrorExitCode = 0 // RARS command exit code to return if assemble error occurs
private       var simulateErrorExitCode = 0 // RARS command exit code to return if simulation error occurs

private fun parseCommandArgs(args: Array<String>): Boolean {
    val noCopyrightSwitch = "nc"
    val displayMessagesToErrSwitch = "me"

    var argsOK = true
    var inProgramArgumentList = false

    programArgumentList = null

    if (args.isEmpty())
        return true // should not get here...

    // If the option to display RARS messages to standard erro is used,
    // it must be processed before any others (since messages may be
    // generated during option parsing).
    processDisplayMessagesToErrSwitch(args, displayMessagesToErrSwitch)
    displayCopyright(args, noCopyrightSwitch) // ..or not..

    if (args.size == 1 && args.first() == "h") {
        displayHelp()
        return false
    }

    for ((index, item) in args.withIndex()) {

        // We have seen "pa" switch, so all remaining args are program args
        // that will become "argc" and "argv" for the program.
        if (inProgramArgumentList) {

            if (programArgumentList == null) {
                programArgumentList = ArrayList()
            }

            programArgumentList!!.add(item)

        }

        // Once we hit "pa", all remaining command args are assumed
        // to be program arguments.
        if (item.lowercase(Locale.getDefault()) == "pa") {
            inProgramArgumentList = true

            continue
        }

        // messages-to-standard-error switch already processed, so ignore.
        if (item.lowercase(Locale.getDefault()) == displayMessagesToErrSwitch) {
            continue
        }

        // no-copyright switch already processed, so ignore.
        if (item.lowercase(Locale.getDefault()) == noCopyrightSwitch) {
            continue
        }

        if (item.lowercase(Locale.getDefault()) == "dump") {

            if (args.size <= index + 3) {
                out!!.println("Dump command line argument requires a segment, format and file name.");
                argsOK = false

            } else {

                if (dumpTriples == null) {
                    dumpTriples = ArrayList()

                }

                dumpTriples!!.add(
                    arrayOf(
                        args[index + 1],
                        args[index + 1],
                        args[index + 1]
                    )
                )

            }

            continue
        }

        if (item.lowercase(Locale.getDefault()) == "mc") {
            val configName = args[index + 1]
            val config = MemoryConfigurations.getConfigurationByName(configName)

            if (config == null) {
                out!!.println("Invalid memory configuration: $configName")
                argsOK = false

            } else {
                MemoryConfigurations.setCurrentConfiguration(config)

            }

            continue
        }

        // Set RARS exit code for assemble error
        if (item.lowercase(Locale.getDefault()).indexOf("ae") == 0) {
            val subString = item.substring(2)

            try {
                assembleErrorExitCode = Integer.decode(subString)
                continue

            } catch (_: NumberFormatException) {

            }
        }

        if (item.lowercase(Locale.getDefault()).indexOf("se") == 0) {
            val subString = item.substring(2)

            try {
                assembleErrorExitCode = Integer.decode(subString)
                continue

            } catch (_: NumberFormatException) {

            }

        }

        when (item.lowercase(Locale.getDefault())) {

            "d" -> {
                Globals.debug = true
                continue

            }

            "a" -> {
                simulate = false
                continue
            }

            "ad", "da" -> {
                Globals.debug = true
                continue
            }

            "p" -> {
                assembleProject = true
                continue
            }

            "dec" -> {
                displayFormat = DECIMAL
                continue
            }

            "g" -> {
                gui = true
                continue
            }

            "hex" -> {
                displayFormat = HEXADECIMAL
                continue
            }

            "ascii" -> {
                displayFormat = ASCII
                continue
            }

            "b" -> {
                verbose = false
                continue
            }

            "np", "ne" -> {
                options!!.pseudo = false
                continue
            }

            "we" -> {
                options!!.warningsAreErrors = true
                continue
            }

            "sm" -> {
                options!!.startAtMain = true
                continue
            }

            "smc" -> {
                options!!.selfModifyingCode = true
                continue
            }

            "rv64" -> {
                rv64 = true
                continue
            }

            "ic" -> {
                countInstructions = true
                continue
            }
        }

        if (File(item).exists()) {
            filenameList?.add(item)
            continue
        }

        if (item.indexOf("x") == 0) {

            if (
                RegisterFile.getRegister(item)              == null &&
                FloatingPointRegisterFile.getRegister(item) == null
            ) {
                out!!.println("Invalid Register Name: $item")

            } else {
                registerDisplayList!!.add(item)
            }

            continue
        }

        // check for register name w/o $.
        if (
            RegisterFile.getRegister(item) != null ||
            FloatingPointRegisterFile.getRegister(item) != null ||
            ControlAndStatusRegisterFile.getRegister(item) != null
        ) {
            registerDisplayList!!.add(item)
            continue

        }

        // Check for stand-alone integer, which is the max execution steps option
        try {
            Integer.decode(item)
            options!!.maxSteps = Integer.decode(item)
            continue

        } catch (_: NumberFormatException) {

        }

        // Check for integer address range (m-n)
        try {
            val memoryRange = checkMemoryAddressRange(item)

            memoryDisplayList!!.add(memoryRange!![0])
            memoryDisplayList!!.add(memoryRange[1])
            continue

        } catch (_: NumberFormatException) {
            out!!.println("Invalid/unaligned address or invalid range: $item")
            argsOK = false

            continue

        } catch (_: NullPointerException) {

        }

        out!!.println("Invalid Command Argument: $item")
        argsOK = false
    }

    return argsOK
}

// If option to display RARS messages to standard err (System.err) is
//  present, it must be processed before all others.  Since messages may
//  be output as early as during the command parse.
private fun processDisplayMessagesToErrSwitch(args: Array<String>, displayMessagesToErrSwitch: String) {
    for (arg in args) {
        if (arg.lowercase(Locale.getDefault()) == displayMessagesToErrSwitch) {
            out = System.err
            return
        }
    }
}

// Decide whether copyright should be displayed, and display
//  if so.
private fun displayCopyright(args: Array<String>, noCopyrightSwitch: String) {
    for (arg in args) {
        if (arg.lowercase(Locale.getDefault()) == noCopyrightSwitch) {
            return
        }
    }
    out!!.println("RARS " + Globals.version + "  Copyright " + Globals.copyrightYears + " " + Globals.copyrightHolders + "\n")
}


//  Display command line help text
private fun displayHelp() {
    val segmentNames = MemoryDump.getSegmentNames()
    var segments = ""
    for (i in segmentNames.indices) {
        segments += segmentNames[i]
        if (i < segmentNames.size - 1) {
            segments += ", "
        }
    }
    val dumpFormats = DumpFormatLoader.getDumpFormats()
    var formats = ""
    for (i in dumpFormats.indices) {
        formats += dumpFormats[i].commandDescriptor
        if (i < dumpFormats.size - 1) {
            formats += ", "
        }
    }
    out!!.println("Usage:  Rars  [options] filename [additional filenames]")
    out!!.println("  Valid options (not case sensitive, separate by spaces) are:")
    out!!.println("      a  -- assemble only, do not simulate")
    out!!.println("  ae<n>  -- terminate RARS with integer exit code <n> if an assemble error occurs.")
    out!!.println("  ascii  -- display memory or register contents interpreted as ASCII codes.")
    out!!.println("      b  -- brief - do not display register/memory address along with contents")
    out!!.println("      d  -- display RARS debugging statements")
    out!!.println("    dec  -- display memory or register contents in decimal.")
    out!!.println("   dump <segment> <format> <file> -- memory dump of specified memory segment")
    out!!.println("            in specified format to specified file.  Option may be repeated.")
    out!!.println("            Dump occurs at the end of simulation unless 'a' option is used.")
    out!!.println("            Segment and format are case-sensitive and possible values are:")
    out!!.println("            <segment> = $segments, or a range like 0x400000-0x10000000")
    out!!.println("            <format> = $formats")
    out!!.println("      g  -- force GUI mode")
    out!!.println("      h  -- display this help.  Use by itself with no filename.")
    out!!.println("    hex  -- display memory or register contents in hexadecimal (default)")
    out!!.println("     ic  -- display count of basic instructions 'executed'")
    out!!.println("     mc <config>  -- set memory configuration.  Argument <config> is")
    out!!.println("            case-sensitive and possible values are: Default for the default")
    out!!.println("            32-bit address space, CompactDataAtZero for a 32KB memory with")
    out!!.println("            data segment at address 0, or CompactTextAtZero for a 32KB")
    out!!.println("            memory with text segment at address 0.")
    out!!.println("     me  -- display RARS messages to standard err instead of standard out. ")
    out!!.println("            Can separate messages from program output using redirection")
    out!!.println("     nc  -- do not display copyright notice (for cleaner redirected/piped output).")
    out!!.println("     np  -- use of pseudo instructions and formats not permitted")
    out!!.println("      p  -- Project mode - assemble all files in the same directory as given file.")
    out!!.println("  se<n>  -- terminate RARS with integer exit code <n> if a simulation (run) error occurs.")
    out!!.println("     sm  -- start execution at statement with global label main, if defined")
    out!!.println("    smc  -- Self Modifying Code - Program can write and branch to either text or data segment")
    out!!.println("    rv64 -- Enables 64 bit assembly and executables (Not fully compatible with rv32)")
    out!!.println("    <n>  -- where <n> is an integer maximum count of steps to simulate.")
    out!!.println("            If 0, negative or not specified, there is no maximum.")
    out!!.println(" x<reg>  -- where <reg> is number or name (e.g. 5, t3, f10) of register whose ")
    out!!.println("            content to display at end of run.  Option may be repeated.")
    out!!.println("<reg_name>  -- where <reg_name> is name (e.g. t3, f10) of register whose")
    out!!.println("            content to display at end of run.  Option may be repeated. ")
    out!!.println("<m>-<n>  -- memory address range from <m> to <n> whose contents to")
    out!!.println("            display at end of run. <m> and <n> may be hex or decimal,")
    out!!.println("            must be on word boundary, <m> <= <n>.  Option may be repeated.")
    out!!.println("     pa  -- Program Arguments follow in a space-separated list.  This")
    out!!.println("            option must be placed AFTER ALL FILE NAMES, because everything")
    out!!.println("            that follows it is interpreted as a program argument to be")
    out!!.println("            made available to the program at runtime.")
    out!!.println("If more than one filename is listed, the first is assumed to be the main")
    out!!.println("unless the global statement label 'main' is defined in one of the files.")
    out!!.println("Exception handler not automatically assembled.  Add it to the file list.")
    out!!.println("Options used here do not affect RARS Settings menu values and vice versa.")
}


// Check for memory address subrange.  Has to be two integers separated
// by "-"; no embedded spaces.  e.g. 0x00400000-0x00400010
// If number is not multiple of 4, will be rounded up to next higher.
@Throws(java.lang.NumberFormatException::class)
private fun checkMemoryAddressRange(arg: String): Array<String>? {
    var memoryRange: Array<String>? = null


    if (
        arg.indexOf(rangeSeparator) > 0 &&
        arg.indexOf(rangeSeparator) < arg.length - 1
    ) {
        // assume correct format, two numbers separated by -, no embedded spaces.
        // If that doesn't work it is invalid.
        memoryRange = arrayOf()
        memoryRange[0] = arg.substring(0, arg.indexOf(rangeSeparator))
        memoryRange[1] = arg.substring(arg.indexOf(rangeSeparator) + 1)
        // NOTE: I will use homegrown decoder, because Integer.decode will throw
        // exception on address higher than 0x7FFFFFFF (e.g. sign bit is 1).
        if (Binary.stringToInt(memoryRange[0]) > Binary.stringToInt(memoryRange[1]) || !Memory.wordAligned(
                Binary.stringToInt(
                    memoryRange[0]
                )
            ) || !Memory.wordAligned(Binary.stringToInt(memoryRange[1]))
        ) {
            throw java.lang.NumberFormatException()
        }
    }
    return memoryRange
}

private fun dumpSegments(program: Program?) {
    if (dumpTriples == null || program == null) return

    for (triple in dumpTriples!!) {
        val file = File(triple[2])
        var segInfo = MemoryDump.getSegmentBounds(triple[0])
        // If not segment name, see if it is address range instead.  DPS 14-July-2008
        if (segInfo == null) {
            try {
                val memoryRange = checkMemoryAddressRange(triple[0])
                segInfo = arrayOfNulls(2)
                segInfo[0] = Binary.stringToInt(memoryRange!![0]) // low end of range
                segInfo[1] = Binary.stringToInt(memoryRange[1]) // high end of range
            } catch (nfe: java.lang.NumberFormatException) {
                segInfo = null
            } catch (npe: java.lang.NullPointerException) {
                segInfo = null
            }
        }
        if (segInfo == null) {
            out!!.println("Error while attempting to save dump, segment/address-range " + triple[0] + " is invalid!")
            continue
        }
        val format = DumpFormatLoader.findDumpFormatGivenCommandDescriptor(triple[1])
        if (format == null) {
            out!!.println("Error while attempting to save dump, format " + triple[1] + " was not found!")
            continue
        }
        try {
            val highAddress =
                program.memory.getAddressOfFirstNull(segInfo[0]!!, segInfo[1]!!) - Memory.WORD_LENGTH_BYTES
            if (highAddress < segInfo[0]!!) {
                out!!.println("This segment has not been written to, there is nothing to dump.")
                continue
            }
            format.dumpMemoryRange(file, segInfo[0]!!, highAddress, program.memory)
        } catch (e: FileNotFoundException) {
            out!!.println("Error while attempting to save dump, file $file was not found!")
        } catch (e: AddressErrorException) {
            out!!.println("Error while attempting to save dump, file " + file + "!  Could not access address: " + e.address + "!")
        } catch (e: IOException) {
            out!!.println("Error while attempting to save dump, file $file!  Disk IO failed!")
        }
    }
}

// Carry out the rars command: assemble then optionally run
// Returns false if no simulation (run) occurs, true otherwise.
private fun runCommand(): Program? {
    if (filenameList!!.size == 0) {
        return null
    }

    Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED, rv64)
    InstructionSet.rv64 = rv64
    Globals.instructionSet.populate()

    val mainFile = File(filenameList!![0]).absoluteFile // First file is "main" file
    val filesToAssemble: ArrayList<String>
    if (assembleProject) {
        filesToAssemble = FilenameFinder.getFilenameList(mainFile.parent, Globals.fileExtensions)
        if (filenameList!!.size > 1) {
            // Using "p" project option PLUS listing more than one filename on command line.
            // Add the additional files, avoiding duplicates.
            filenameList!!.removeAt(0) // first one has already been processed
            val moreFilesToAssemble: ArrayList<String> =
                FilenameFinder.getFilenameList(filenameList, FilenameFinder.MATCH_ALL_EXTENSIONS)
            // Remove any duplicates then merge the two lists.
            var index2 = 0
            while (index2 < moreFilesToAssemble.size) {
                for (index1 in filesToAssemble.indices) {
                    if (filesToAssemble[index1] == moreFilesToAssemble[index2]) {
                        moreFilesToAssemble.removeAt(index2)
                        index2-- // adjust for left shift in moreFilesToAssemble...
                        break // break out of inner loop...
                    }
                }
                index2++
            }
            filesToAssemble.addAll(moreFilesToAssemble)
        }
    } else {
        filesToAssemble = FilenameFinder.getFilenameList(filenameList, FilenameFinder.MATCH_ALL_EXTENSIONS)
    }
    val program = Program(options)
    try {
        if (Globals.debug) {
            out!!.println("---  TOKENIZING & ASSEMBLY BEGINS  ---")
        }
        val warnings = program.assemble(filesToAssemble, mainFile.absolutePath)
        if (warnings != null && warnings.warningsOccurred()) {
            out!!.println(warnings.generateWarningReport())
        }
    } catch (e: AssemblyException) {
        Globals.exitCode = assembleErrorExitCode
        out!!.println(e.errors().generateErrorAndWarningReport())
        out!!.println("Processing terminated due to errors.")
        return null
    }
    // Setup for program simulation even if just assembling to prepare memory dumps
    program.setup(programArgumentList, null)
    if (simulate) {
        if (Globals.debug) {
            out!!.println("--------  SIMULATION BEGINS  -----------")
        }
        try {
            while (true) {
                val done = program.simulate()
                if (done == Simulator.Reason.MAX_STEPS) {
                    out!!.println(
                        """
Program terminated when maximum step limit ${options!!.maxSteps} reached."""
                    )
                    break
                } else if (done == Simulator.Reason.CLIFF_TERMINATION) {
                    out!!.println("\nProgram terminated by dropping off the bottom.")
                    break
                } else if (done == Simulator.Reason.NORMAL_TERMINATION) {
                    out!!.println("\nProgram terminated by calling exit")
                    break
                }
                assert(done == Simulator.Reason.BREAKPOINT) { "Internal error: All cases other than breakpoints should be handled already" }
                displayAllPostMortem(program) // print registers if we hit a breakpoint, then continue
            }
        } catch (e: SimulationException) {
            Globals.exitCode = simulateErrorExitCode
            out!!.println(e.error().generateReport())
            out!!.println("Simulation terminated due to errors.")
        }
        displayAllPostMortem(program)
    }
    if (Globals.debug) {
        out!!.println("\n--------  ALL PROCESSING COMPLETE  -----------")
    }
    return program
}

private fun displayAllPostMortem(program: Program) {
    displayMiscellaneousPostMortem(program)
    displayRegistersPostMortem(program)
    displayMemoryPostMortem(program.memory)
}

private fun displayMiscellaneousPostMortem(program: Program) {
    if (countInstructions) {
        out!!.println(
            """
                
                ${program.getRegisterValue("cycle")}
                """.trimIndent()
        )
    }
}

private fun displayMemoryPostMortem(memory: Memory) {
    var value: Int
    // Display requested memory range contents
    val memIter: Iterator<String> = memoryDisplayList!!.iterator()
    var addressStart = 0
    var addressEnd = 0
    while (memIter.hasNext()) {
        try { // This will succeed; error would have been caught during command arg parse
            addressStart = Binary.stringToInt(memIter.next())
            addressEnd = Binary.stringToInt(memIter.next())
        } catch (nfe: java.lang.NumberFormatException) {
        }
        var valuesDisplayed = 0
        var addr = addressStart
        while (addr <= addressEnd) {
            if (addr < 0 && addressEnd > 0) break // happens only if addressEnd is 0x7ffffffc

            if (valuesDisplayed % memoryWordsPerLine == 0) {
                out!!.print(if (valuesDisplayed > 0) "\n" else "")
                if (verbose) {
                    out!!.print("Mem[" + Binary.intToHexString(addr) + "]\t")
                }
            }
            try {
                // Allow display of binary text segment (machine code) DPS 14-July-2008
                if (Memory.inTextSegment(addr)) {
                    val iValue = memory.getRawWordOrNull(addr)
                    value = iValue ?: 0
                } else {
                    value = memory.getWord(addr)
                }
                out!!.print(formatIntForDisplay(value) + "\t")
            } catch (aee: AddressErrorException) {
                out!!.print("Invalid address: $addr\t")
            }
            valuesDisplayed++
            addr += Memory.WORD_LENGTH_BYTES
        }
        out!!.println()
    }
}


// Displays requested register or registers
private fun displayRegistersPostMortem(program: Program) {
    // Display requested register contents
    for (reg in registerDisplayList!!) {
        if (FloatingPointRegisterFile.getRegister(reg) != null) {
            //TODO: do something for double vs float
            // It isn't clear to me what the best behaviour is
            // floating point register
            val ivalue = program.getRegisterValue(reg)
            val fvalue = java.lang.Float.intBitsToFloat(ivalue)
            if (verbose) {
                out!!.print(reg + "\t")
            }
            if (displayFormat == HEXADECIMAL) {
                // display float (and double, if applicable) in hex
                out!!.println(Binary.intToHexString(ivalue))
            } else if (displayFormat == DECIMAL) {
                // display float (and double, if applicable) in decimal
                out!!.println(fvalue)
            } else { // displayFormat == ASCII
                out!!.println(Binary.intToAscii(ivalue))
            }
        } else if (ControlAndStatusRegisterFile.getRegister(reg) != null) {
            out!!.print(reg + "\t")
            out!!.println(formatIntForDisplay(ControlAndStatusRegisterFile.getRegister(reg).value.toInt()))
        } else if (verbose) {
            out!!.print(reg + "\t")
            out!!.println(formatIntForDisplay(RegisterFile.getRegister(reg).value.toInt()))
        }
    }
}

private fun formatIntForDisplay(value: Int): String {
    return when (displayFormat) {
        DECIMAL -> "" + value
        HEXADECIMAL -> Binary.intToHexString(value)
        ASCII -> Binary.intToAscii(value)
        else -> Binary.intToHexString(value)
    }
}

fun main(args: Array<String>) = application {
    Globals.initialize()

    options = Options()
    gui = args.isEmpty()
    simulate = true
    displayFormat = HEXADECIMAL
    verbose = true
    assembleProject = false
    countInstructions = false
    instructionCount = 0
    assembleErrorExitCode = 0
    simulateErrorExitCode = 0
    registerDisplayList = ArrayList()
    memoryDisplayList = ArrayList()
    filenameList = ArrayList()

    MemoryConfigurations.setCurrentConfiguration(
        MemoryConfigurations.getDefaultConfiguration()
    )

    out = System.out

    if (!parseCommandArgs(args))
        System.exit(Globals.exitCode)

    if (gui) {
        Window(onCloseRequest = ::exitApplication) {
            Home(
                nameApp = "RARS " + Globals.version,
                files   = filenameList!!
            )
        }

    } else {
        System.setProperty("java.awt.headless", "true")

        dumpSegments(runCommand())
        exitProcess(Globals.exitCode)
    }
}