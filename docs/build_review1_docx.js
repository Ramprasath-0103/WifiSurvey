const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType,
  PageBreak, LevelFormat, convertInchesToTwip
} = require("docx");

const FONT = "Times New Roman";
const W = 9020;                                  // usable width in DXA (A4, 1in margins)

/* ---------- small builders ---------- */
const P = (text, o = {}) => new Paragraph({
  alignment: o.align || AlignmentType.JUSTIFIED,
  spacing: { after: o.after === undefined ? 140 : o.after, line: 300 },
  indent: o.indent,
  children: [new TextRun({ text, bold: !!o.bold, italics: !!o.italic, size: o.size || 24, font: FONT })]
});

const H1 = (text) => new Paragraph({
  heading: HeadingLevel.HEADING_1,
  spacing: { before: 280, after: 140 },
  children: [new TextRun({ text, bold: true, size: 28, font: FONT, color: "000000" })]
});

const H2 = (text) => new Paragraph({
  heading: HeadingLevel.HEADING_2,
  spacing: { before: 200, after: 100 },
  children: [new TextRun({ text, bold: true, size: 25, font: FONT, color: "000000" })]
});

const BULLET = (text, lvl = 0) => new Paragraph({
  numbering: { reference: "bullets", level: lvl },
  spacing: { after: 70, line: 290 },
  children: [new TextRun({ text, size: 24, font: FONT })]
});

const NUM = (text) => new Paragraph({
  numbering: { reference: "nums", level: 0 },
  spacing: { after: 70, line: 290 },
  children: [new TextRun({ text, size: 24, font: FONT })]
});

const cell = (text, { bold = false, w, shade, align } = {}) => new TableCell({
  width: { size: w, type: WidthType.DXA },
  shading: shade ? { type: ShadingType.CLEAR, fill: shade, color: "auto" } : undefined,
  margins: { top: 70, bottom: 70, left: 110, right: 110 },
  children: (Array.isArray(text) ? text : [text]).map((line, i, arr) => new Paragraph({
    alignment: align || AlignmentType.LEFT,
    spacing: { after: i === arr.length - 1 ? 0 : 40, line: 270 },
    children: [new TextRun({ text: String(line), bold, size: 21, font: FONT })]
  }))
});

function table(widths, header, rows) {
  const border = { style: BorderStyle.SINGLE, size: 4, color: "999999" };
  return new Table({
    columnWidths: widths,
    width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    borders: { top: border, bottom: border, left: border, right: border,
               insideHorizontal: border, insideVertical: border },
    rows: [
      new TableRow({
        tableHeader: true,
        children: header.map((h, i) => cell(h, { bold: true, w: widths[i], shade: "EDEAE4" }))
      }),
      ...rows.map(r => new TableRow({ children: r.map((c, i) => cell(c, { w: widths[i] })) }))
    ]
  });
}


/* a bordered, centred placeholder box for a figure to be pasted in */
const FIGSLOT = (caption, filename) => [
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 200, after: 60 },
    border: { top:    { style: BorderStyle.DASHED, size: 6, color: "AAAAAA", space: 14 },
              bottom: { style: BorderStyle.DASHED, size: 6, color: "AAAAAA", space: 14 },
              left:   { style: BorderStyle.DASHED, size: 6, color: "AAAAAA", space: 14 },
              right:  { style: BorderStyle.DASHED, size: 6, color: "AAAAAA", space: 14 } },
    children: [new TextRun({ text: "[ paste " + filename + " here ]", italics: true, size: 22, color: "777777", font: FONT })]
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 220 },
    children: [new TextRun({ text: caption, bold: true, size: 21, font: FONT })]
  })
];

const GAP = (after = 160) => new Paragraph({ spacing: { after }, children: [] });

/* ================== CONTENT ================== */
const body = [];

/* ---- cover ---- */
body.push(
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 900, after: 60 },
    children: [new TextRun({ text: "FALL SEMESTER 2026 - 2027", bold: true, size: 26, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 700 },
    children: [new TextRun({ text: "BCSE308P - COMPUTER NETWORKS LABORATORY", bold: true, size: 26, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 },
    children: [new TextRun({ text: "REVIEW 1", bold: true, size: 32, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 800 },
    children: [new TextRun({ text: "Problem Identification and Planning", size: 26, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 },
    children: [new TextRun({ text: "Real-Time Network Quality Mapping and", bold: true, size: 30, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 },
    children: [new TextRun({ text: "Connectivity Analysis", bold: true, size: 30, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 900 },
    children: [new TextRun({ text: "An indoor wireless survey that separates weak coverage from network congestion", italics: true, size: 24, font: FONT })] })
);

body.push(table([2400, 3200, 3420],
  ["Reg. No.", "Name", "Programme"],
  [["24BCE0176", "Ram Prasath R P", "B.Tech CSE"],
   ["24BCT0151", "Nehal Arun", "B.Tech CSE (Blockchain Technology)"]]));

body.push(
  GAP(500),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 },
    children: [new TextRun({ text: "Faculty In-charge: Dr. Tamizharasi", size: 24, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 },
    children: [new TextRun({ text: "Vellore Institute of Technology, Vellore", size: 24, font: FONT })] }),
  new Paragraph({ children: [new PageBreak()] })
);

/* ---- 1 ---- */
body.push(H1("1. Project Title"));
body.push(P("Real-Time Network Quality Mapping and Connectivity Analysis: An Indoor Wireless Survey Correlating Signal Strength with Medium-Level Behaviour."));
body.push(P("In shorter words, we are mapping one floor of a campus building to find out where the Wi-Fi is bad, and more importantly, why it is bad."));

/* ---- 2 ---- */
body.push(H1("2. Introduction"));
body.push(P("Wi-Fi inside a college building is never evenly good. Everyone already knows which corridor drops video calls and which classroom is fine, and most people assume the reason is signal strength. You move closer to the access point and things get better, so weak signal must be the problem."));
body.push(P("That explanation is only half right. Wi-Fi is a shared medium. Devices on the same channel take turns transmitting, and a device only speaks when it senses the air is free. So a point with a very strong signal can still be slow if forty other phones are attached to the same access point and all of them want the channel. The waiting is what kills the speed, not the distance."));
body.push(P("From the user's side, both failures look the same. The page loads slowly either way. But the fixes are opposite. If an area is bad because of distance or walls, adding an access point genuinely helps. If it is bad because too many clients are sharing the air, then adding another access point on the same channel actually makes things worse, since the new AP joins the same contention domain and adds one more device competing for airtime."));
body.push(P("This project takes one floor of an academic block, marks about thirty fixed points on a grid, and measures each of them twice a day using an Android app we are writing ourselves. At every point we record the signal strength, how busy the access point says the channel is, and how the network actually performs. Then we use those numbers to label each point as coverage-limited, capacity-limited, both, or fine."));

/* ---- 3 ---- */
body.push(H1("3. Problem Statement"));
body.push(P("There is currently no simple way, using only equipment a student already owns, to explain why a specific location inside a campus building has poor Wi-Fi."));
body.push(P("Speed test apps will tell you the connection is slow but give no reason. Wi-Fi analyser apps will show you the signal strength but say nothing about whether the network is usable. Neither of them ties the two together, and neither records anything against a fixed location, so you cannot compare one corridor against another, or the same corridor in the morning against the same corridor at night."));
body.push(P("The problem we are addressing is therefore:"));
body.push(P("To design and build a repeatable measurement method that captures both signal-level and medium-level conditions at fixed indoor locations, links them to the performance a real user would experience at that spot, and produces a per-location diagnosis of whether the limiting factor is coverage or capacity, using only a standard Android phone and no specialised survey hardware.", { indent: { left: 400, right: 400 }, italic: true }));

/* ---- 4 ---- */
body.push(H1("4. Motivation"));
body.push(P("Two things pushed us towards this topic."));
body.push(P("The first is just personal irritation. Both of us have sat in the same seat in the same block and had the internet work perfectly at ten in the morning and be unusable at eight at night. The signal bar on the phone does not change. Something else is going on, and we wanted to know what."));
body.push(P("The second is that the standard reaction to a Wi-Fi complaint is almost always \"install another access point there\". For an area that is genuinely out of range, that is correct. For an area that is congested, it is the wrong move and can make the problem worse. This is a known result in wireless networking, but we wanted to see if we could demonstrate it with measurements taken from our own building instead of just quoting it out of a textbook."));
body.push(P("There is also a practical reason. Professional site survey tools like Ekahau or NetSpot do this kind of work properly, but they cost money and often need special adapters. If a method built around a normal phone can produce a useful diagnosis, then any department could repeat it without a budget. That felt like a project worth doing rather than just an exercise."));

/* ---- 5 ---- */
body.push(H1("5. Objectives"));
body.push(P("What we intend to achieve by the end of the semester:"));
[
 "Build an Android application that automatically records Wi-Fi context and network performance at a selected reference point, in a single run of about ninety seconds, with no manual note-taking.",
 "Read medium-level information directly from access point beacons, specifically the BSS Load element, which reports channel utilisation and the number of connected stations.",
 "Measure latency, jitter and packet loss against the default gateway, and download and upload throughput against a fixed endpoint, including latency measured while the link is loaded.",
 "Survey a single floor at roughly thirty fixed grid points, three repetitions each, in two fixed time windows (morning and evening) so that load-related effects can be separated from permanent ones.",
 "Store everything in a PostgreSQL database and run a correlation analysis between each Wi-Fi indicator and each performance metric.",
 "Classify every point as coverage-limited, capacity-limited, mixed or unconstrained, and attach a specific recommendation to each category.",
 "Present the results as a grid map of the floor that can be coloured by any metric, with signal strength and measured throughput shown side by side.",
 "Run a small ns-3 simulation study to demonstrate that contention alone, with signal held constant, is enough to cause the kind of slowdown we expect to observe."
].forEach(t => body.push(NUM(t)));

/* ---- 6 ---- */
body.push(H1("6. Existing System"));
body.push(P("Work of this kind already exists, but it is split across four fairly separate categories, and each one solves only part of the problem."));

body.push(H2("6.1 Commercial site survey software"));
body.push(P("Tools such as Ekahau AI Pro, NetSpot and NetAlly AirMagnet are the industry standard for indoor wireless surveys. The surveyor walks a floor plan with a laptop, and the software builds heatmaps of signal strength, interference and predicted throughput. They are accurate and well established."));

body.push(H2("6.2 Free Wi-Fi analyser applications"));
body.push(P("Android apps like WiFiAnalyzer (the open source one from VREM Software) show every visible access point along with its BSSID, channel and current RSSI. They are useful for a quick look at what is nearby and for spotting channel overlap."));

body.push(H2("6.3 Speed test applications"));
body.push(P("Ookla Speedtest, Fast.com and similar services measure download, upload and ping against a remote server. Almost everyone has used one. They answer the question \"is it slow right now\" and nothing else."));

body.push(H2("6.4 Controller-side network monitoring"));
body.push(P("Enterprise wireless controllers from Cisco, Aruba and others keep detailed statistics per access point, including client counts, channel utilisation and retry rates. This data is genuinely good, but it belongs to the network administrators."));

/* ---- 7 ---- */
body.push(H1("7. Limitations of the Existing System"));
body.push(P("Putting those four categories side by side, the gaps become clear."));
body.push(GAP(80));
body.push(table([2350, 3200, 3470],
  ["Existing approach", "What it gives you", "Where it falls short"],
  [
   ["Commercial survey tools (Ekahau, NetSpot)",
    "Accurate heatmaps, professional reporting",
    "Licence cost is well beyond a student project, and full functionality usually needs a supported external adapter. Mostly reports signal, not the cause of slowness."],
   ["Wi-Fi analyser apps",
    "Live RSSI, BSSID, channel, neighbour list",
    "Nothing is logged against a location, so results cannot be compared between points or between times. No throughput or latency measurement at all."],
   ["Speed test apps",
    "Download, upload, ping to a remote server",
    "No Wi-Fi context whatsoever. A bad result could be the wireless link, the campus uplink or the remote server, and the app cannot tell you which."],
   ["Controller dashboards",
    "Client counts, utilisation and retries per AP",
    "Students have no access. It is also an AP-side view, so it shows what the access point sees rather than what a user standing in a particular corridor experiences."],
   ["Monitor-mode frame capture (Wireshark)",
    "Full frame-level detail including retries and data rates",
    "Requires a USB adapter with a chipset that supports monitor mode, which we do not have. Also raises permission and privacy questions on a campus network."]
  ]));
body.push(GAP(120));
body.push(P("The common thread is that no single tool in the list connects the radio layer to the user-visible performance layer at the same place and the same moment. That connection is exactly what is needed to tell coverage problems apart from capacity problems, and it is the gap this project is trying to fill."));

/* ---- 8 ---- */
body.push(H1("8. Proposed System"));
body.push(P("We are building a measurement system that runs entirely on one Android phone, plus an offline analysis and visualisation stage on a laptop. The phone never needs to talk to a server during the survey, which removes a whole set of problems we ran into while planning (described in section 8.4)."));

body.push(H2("8.1 The survey grid"));
body.push(P("Instead of a scanned floor plan image, we lay the floor out as a simple lettered and numbered grid. Rows are letters, columns are numbers, so a point is identified as C5 or A10. Each point also carries a plain description like \"corridor outside Lab 101\" so that we can find the same spot again. One point is marked as a control point, and it is measured first in every session for reasons explained below."));
body.push(P("We are using twenty-seven points on one floor at the moment. The grid approach is simpler to set up than digitising a floor plan, it is easy to reproduce, and it renders cleanly in the final map."));

body.push(H2("8.2 What the app measures"));
body.push(P("One button press runs eight stages in sequence and takes roughly ninety seconds."));
body.push(GAP(80));
body.push(table([700, 1250, 7070],
  ["Stage", "Duration", "What happens"],
  [
   ["1", "about 3 s", "Wi-Fi scan. Records the serving access point and every neighbour visible, with BSSID, channel, band and RSSI. Parses the BSS Load element out of the beacon."],
   ["2", "instant", "Reads the current connection: RSSI, frequency, BSSID, and the negotiated transmit and receive link speeds."],
   ["3", "about 12 s", "Idle latency. Fifty pings to the default gateway. Gives median, 95th percentile, jitter and packet loss."],
   ["4", "15 s", "Download throughput, timed, with the first two seconds discarded so that TCP slow start does not drag the number down."],
   ["5", "runs with 4", "Loaded latency. A second ping run to the gateway while the download is saturating the link."],
   ["6", "15 s", "Upload throughput, timed the same way."],
   ["7", "about 8 s", "Recovery ping after the load stops, to confirm the link settled back to baseline."],
   ["8", "about 3 s", "Second Wi-Fi scan, so we can see whether channel utilisation changed after we loaded the network ourselves."]
  ]));
body.push(GAP(120));
body.push(P("Everything is written into a local database on the phone straight away, so a dropped connection in a stairwell does not lose the reading. After the session the data is exported as a JSON file and imported into PostgreSQL on the laptop."));

body.push(H2("8.3 Analysis and output"));
body.push(P("On the laptop side, a Python pipeline joins the measurements with their beacon and neighbour data, takes the median of the three repetitions per point, and runs both Pearson and Spearman correlations between every Wi-Fi indicator and every performance metric. Each point then gets a quality score and a category from the following matrix."));
body.push(GAP(80));
body.push(table([2300, 2240, 2240, 2240],
  ["", "Channel util below 30%", "Channel util 30 to 50%", "Channel util above 50%"],
  [
   ["RSSI at or above -67 dBm", "Unconstrained", "Mixed", "Capacity-limited"],
   ["RSSI -67 to -75 dBm", "Mixed", "Mixed", "Capacity-limited"],
   ["RSSI below -75 dBm", "Coverage-limited", "Coverage-limited", "Mixed (both)"]
  ]));
body.push(GAP(120));
body.push(P("The thresholds are the conventional operational ones used in enterprise wireless design rather than anything defined by a standard, and we will say so in the final report and show the scatter plot with the boundary drawn on it."));
body.push(P("The headline output is a pair of grid maps of the same floor, one coloured by signal strength and one coloured by measured throughput. Where those two disagree is the interesting part of the whole project."));

body.push(H2("8.4 A constraint we had to design around"));
body.push(P("Our original plan put a laptop on the campus network over Ethernet and ran a small test server on it, so that the speed test would only cross the wireless hop. We could not do that. There was no Ethernet port we were allowed to use, running the server on a lab desktop was not available to us either, and putting the laptop on Wi-Fi instead would add a second wireless hop of unknown quality. Campus networks also commonly block traffic between two wireless clients, which would have stopped it working at all."));
body.push(P("So we moved to a phone-only design. Latency, jitter and packet loss are measured against the default gateway, which is one hop past the access point and is always reachable, so those numbers stay confined to the wireless link. Throughput is measured against a fixed public endpoint, which means it includes the campus uplink as well as the wireless hop."));
body.push(P("To keep that honest we do three things. We use the same endpoint for the entire study. We take a control measurement at one fixed strong-signal point at the start of every session, so if the campus uplink is congested on a particular evening we can see it in the control rather than blaming a survey point. And all of our conclusions are stated as relative comparisons between points or between sessions, never as absolute speeds. This limitation is documented rather than hidden, and the control measurement is treated as mandatory."));

/* ---- 9 ---- */
body.push(H1("9. Novelty and Innovation"));
body.push(P("Indoor Wi-Fi surveying is not new. What is new here is the way we have put the pieces together."));
[
 "Signal and load measured together. At every point we note how strong the signal is and how busy the channel is, at the same moment. Most free apps give you one or the other, never both.",
 "It gives a reason, not just a colour. Other tools draw a red patch and stop there. Ours says why the patch is red, and what to do about it.",
 "Same spot, morning and night. Walls do not move during the day. So if the signal is the same but the speed drops in the evening, the crowd is the reason.",
 "No extra hardware. No special adapter, no laptop carried around the building. One Android phone does the whole thing.",
 "Real and simulated data stay separate. Every simulated figure is labelled, and the database will not accept a simulated row unless it is marked as one."
].forEach(t => body.push(BULLET(t)));
body.push(GAP(60));
body.push(P("One result is worth pointing out early. If an area is slow because it is crowded, adding another access point on the same channel makes it slower, not faster. That is the opposite of what most people would guess, and it is the kind of thing this project is meant to catch."));

/* ---- 10 ---- */
body.push(new Paragraph({ children: [new PageBreak()] }));
body.push(H1("10. Literature Survey"));
body.push(P("Five sources did most of the work in shaping this project."));
body.push(GAP(80));
body.push(table([450, 3170, 5400],
  ["#", "Source", "What we took from it"],
  [
   ["1", "IEEE Std 802.11-2020, Wireless LAN MAC and PHY Specifications",
    "The layout of the BSS Load element we read out of beacons: station count, channel utilisation, admission capacity. It also says the element is optional, which is why we check for it before relying on it."],
   ["2", "J. Gettys and K. Nichols, \"Bufferbloat: Dark Buffers in the Internet\", ACM Queue, vol. 9, no. 11, 2011",
    "Ping on an idle link tells you very little. This is why we also ping while the download is running."],
   ["3", "Android Developers documentation, ScanResult and Wi-Fi scanning",
    "getInformationElements() only exists from Android 11 onwards, which set our minimum phone requirement. The same docs give the limit of four scans per two minutes that we design around."],
   ["4", "Cisco and Aruba enterprise WLAN design guides",
    "Where our -67 dBm and 50 percent utilisation thresholds come from. They are working rules of thumb rather than standards, and we say so in the report."],
   ["5", "G. F. Riley and T. R. Henderson, \"The ns-3 Network Simulator\", Springer, 2010",
    "Background for the simulation. Also why we let the simulated link pick its own data rate instead of fixing it, since a fixed rate would hide the effect we are looking for."]
  ]));
body.push(GAP(140));
body.push(P("The gap we noticed while reading is that signal mapping and performance testing are almost always treated as two separate jobs, done with separate tools, usually by people who already have access to the network equipment. Very little of it is written from the position of an ordinary phone with no special access, which is where a student actually stands."));

/* ---- 11 ---- */
body.push(H1("11. Tools and Technologies"));
body.push(GAP(80));
body.push(table([2000, 2500, 4520],
  ["Layer", "Technology", "Why this one"],
  [
   ["Survey application", "Kotlin, Android SDK 35, minimum SDK 30",
    "Native Kotlin gives direct access to WifiManager and the beacon information elements. API 30 is a hard floor because the element parsing API does not exist below Android 11."],
   ["On-phone storage", "Room (SQLite)",
    "Readings are written locally before anything else happens, so a dead spot or an app crash mid-survey does not cost us the data."],
   ["Data export", "JSON file written to device storage",
    "Simplest possible transfer. No server, no sync, nothing to fail in a corridor."],
   ["Database", "PostgreSQL 14",
    "Around 160 measurements, each with a set of neighbour and beacon rows attached. That is past the point where a spreadsheet stays reliable, and the relational structure makes the analysis queries straightforward."],
   ["Analysis", "Python 3, pandas, SciPy",
    "pandas for joining and aggregating the tables, SciPy for the Pearson and Spearman correlations. Standard and well documented."],
   ["Visualisation", "HTML, CSS and JavaScript grid renderer",
    "The floor is a grid, so the map is a grid. Renders identically everywhere and needs no mapping library or image assets."],
   ["Simulation", "ns-3 (802.11ac, Yans channel model, Minstrel HT rate control)",
    "Lets us hold signal constant and vary only the number of competing stations, which is impossible to arrange on a live campus network."],
   ["Capture analysis", "tshark",
    "Reads the radiotap format packet captures the simulator writes, giving retry ratio, airtime and data rate distribution."],
   ["Build tooling", "Gradle 8.11, JUnit",
    "Standard Android build. JUnit runs the parser tests on a plain JVM, so the element decoding can be verified without a phone."],
   ["Version control", "Git",
    "Both of us work on the same repository."]
  ]));

/* ---- 12 ---- */
body.push(new Paragraph({ children: [new PageBreak()] }));
body.push(H1("12. Initial System Architecture and Workflow"));

body.push(H2("12.1 Architecture"));
body.push(P("The system has two halves, and they never run at the same time."));
body.push(P("The phone does all the measuring while we are walking the floor. It listens to what the access points are broadcasting, pings the gateway, and runs a speed test. Each reading is saved on the phone the moment it is taken, so a dead spot in a stairwell cannot cost us a point."));
body.push(P("The laptop does everything after that, sitting on a desk. One file gets copied off the phone and loaded into a database. From there the analysis works out what is actually wrong at each point and draws the maps."));
body.push(P("The part that matters is that the laptop takes no part in a measurement. Nothing needs to be plugged in, nothing needs to be reachable over the network, and none of it breaks if the campus blocks one device from talking to another. That is the whole reason the phone-only design was worth the trouble, as described in section 8.4."));
body.push(...FIGSLOT("Figure 1. System architecture", "fig1_architecture.png"));

body.push(H2("12.2 Workflow of a survey session"));
body.push(P("A session is one pass of the floor in one time window. We always measure the control point first, then work through the grid points in order, three readings at each. At roughly five minutes a point including the walking, one pass takes about two and a half hours."));
body.push(P("The file is exported before we leave the building, and copied off the phone the same day."));
body.push(...FIGSLOT("Figure 2. Survey workflow", "fig2_workflow.png"));

/* ---- 13 ---- */
body.push(H1("13. Project Timeline"));
body.push(P("The plan is arranged around the three review dates. Work already finished is marked as such."));
body.push(GAP(80));
body.push(table([1750, 2050, 4100, 1120],
  ["Period", "Phase", "Work", "Status"],
  [
   ["Sep 1 to Sep 18", "Planning and setup",
    "Chose the building and the floor. Checked we had a phone running Android 11, and that the gateway replies to ping. Asked for permission. Built the database and a small grid tool for laying out the twenty-seven points.",
    "Done"],
   ["Sep 19 to Oct 2", "Core app parts",
    "Started with the hardest bit first, which is pulling channel utilisation out of the beacon, since that was the part most likely to fail. After that, the ping and speed test pieces.",
    "In progress"],
   ["Oct 3 to Oct 9", "Full app and trial run",
    "All eight measurement steps working one after the other, saved on the phone and exported to the laptop. Trial run at five points to find the problems before doing the whole floor. Review 2.",
    "Planned"],
   ["Oct 10 to Oct 23", "Survey and analysis",
    "Walk every point, morning and evening, three times each. Then work out which points are held back by weak signal and which by crowding. Simulation runs alongside this.",
    "Planned"],
   ["Oct 24 to Oct 30", "Maps, report and demo",
    "Grid maps of the floor, a list of the worst points with the reason for each, the final report and the live demo. Review 3.",
    "Planned"]
  ]));
body.push(GAP(140));
body.push(P("The pilot survey in the first week of October is the part we are least willing to skip. Whatever breaks across five points would have broken across thirty, and finding it a fortnight early is the difference between a finished project and a scramble."));
body.push(P("We have also agreed an order for cutting scope if we run short of time. The simulation study goes first, then the interpolated view of the map, then the upload test, then the number of repetitions. What we will not cut is the morning against evening comparison, the beacon load collection, and the side by side map, because between them those three are the project."));

/* ---- 14 ---- */
body.push(H1("14. Team Member Responsibilities"));
body.push(P("Both of us walk the survey route together, since one person operates the phone and the other keeps the anomaly log and the timing. Outside the fieldwork the split is as follows."));
body.push(GAP(80));
body.push(table([2600, 6420],
  ["Member", "Responsibilities"],
  [
   [["Ram Prasath R P", "24BCE0176"],
    "Android application: the measurement sequence, beacon element parsing, ping and throughput engines, local storage and export. Database schema and the Python import path. Analysis pipeline including the correlation work, quality scoring and the classification logic."],
   [["Nehal Arun", "24BCT0151"],
    "Survey design and the grid layout, including choosing the points and the control location. Running and logging the field sessions. The ns-3 simulation study and the extraction of its results. The grid map dashboard. Literature survey and the written reports."],
   ["Shared",
    "Week 0 feasibility checks, the permission request, both time windows of the full survey, the pilot, reviewing each other's findings, and preparing the final presentation and demonstration."]
  ]));
body.push(GAP(200));
body.push(P("Current status in one line: the database, the grid tool and the beacon parsing app are built and tested, and the parser has been verified against the byte layout given in the standard. Next is the ping and throughput stage, then the pilot.", { italic: true }));

/* ================== DOC ================== */
const doc = new Document({
  creator: "Ram Prasath R P, Nehal Arun",
  title: "Review 1 - Real-Time Network Quality Mapping and Connectivity Analysis",
  description: "BCSE308P Computer Networks Laboratory, Review 1",
  numbering: {
    config: [
      { reference: "bullets", levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 260 } } } }] },
      { reference: "nums", levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 260 } } } }] }
    ]
  },
  styles: { default: { document: { run: { font: FONT, size: 24 } } } },
  sections: [{
    properties: { page: { margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    children: body
  }]
});

Packer.toBuffer(doc).then(b => {
  fs.writeFileSync("Review1_NetworkQualityMapping.docx", b);
  console.log("written:", b.length, "bytes");
});
