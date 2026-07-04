import genanki

MODEL = genanki.Model(
    1749300001,
    "CFA Basic",
    fields=[{"name": "Front"}, {"name": "Back"}],
    templates=[{
        "name": "Card 1",
        "qfmt": "{{Front}}",
        "afmt": '{{FrontSide}}<hr id="answer">{{Back}}',
    }],
)

# (topic, deck_id, [(front, back), ...])
TOPICS = [
    ("Ethics", 1749310001, [
        ("Under the CFA Code &amp; Standards, if local law conflicts with the Code, which governs?",
         "The <b>stricter</b> of the two — comply with the more strict of applicable law or the Code and Standards."),
        ("Standard I(C) Misrepresentation: is <i>unintentional</i> plagiarism a violation?",
         "Yes. Omitting attribution violates Standard I(C) regardless of intent."),
    ]),
    ("Quantitative Methods", 1749310002, [
        ("What does adjusted R&sup2; correct for versus R&sup2; in multiple regression?",
         "It penalizes adding regressors: adjusted R&sup2; rises only if a new variable improves fit more than chance would predict."),
        ("What is the classic symptom of multicollinearity?",
         "Inflated standard errors &rarr; insignificant t-stats despite a significant F-test and high R&sup2;."),
    ]),
    ("Financial Reporting", 1749310003, [
        ("Under the equity method, how is the investor's share of investee dividends treated?",
         "As a <b>reduction of the investment carrying amount</b>, not as income."),
        ("Rising prices: LIFO vs FIFO effect on COGS and net income?",
         "LIFO &rarr; higher COGS, lower net income, lower ending inventory than FIFO."),
    ]),
    ("Equity Investments", 1749310004, [
        ("In a two-stage DDM, how is the terminal value computed?",
         "Gordon growth on the first stable-growth dividend: V = D<sub>n+1</sub> / (r &minus; g<sub>stable</sub>)."),
        ("Key assumption of the Gordon growth model?",
         "Dividends grow at a constant rate g forever, with r &gt; g."),
    ]),
    ("Fixed Income", 1749310005, [
        ("What does effective duration capture that modified duration does not?",
         "Sensitivity of price to a shift in the <b>benchmark yield curve</b> for bonds with embedded options / uncertain cash flows."),
        ("How can a call option make a bond's effective convexity negative?",
         "When yields fall, the price is compressed toward the call price &rarr; negative convexity."),
    ]),
    ("Derivatives", 1749310006, [
        ("Binomial model: risk-neutral probability of an up move?",
         "&pi; = (1 + r &minus; d) / (u &minus; d)."),
        ("Value of a forward contract at initiation in a no-arbitrage market?",
         "<b>Zero</b> — the forward price is set so no cash changes hands at initiation."),
    ]),
    ("Portfolio Management", 1749310007, [
        ("In the market (single-index) model, what does beta measure?",
         "Systematic risk — sensitivity of the asset's return to the market return."),
    ]),
    ("Economics", 1749310008, [
        ("Under covered interest rate parity, which currency trades at a forward discount?",
         "The <b>higher interest-rate</b> currency trades at a forward discount."),
    ]),
]

decks = []
total = 0
for topic, deck_id, cards in TOPICS:
    d = genanki.Deck(deck_id, f"CFA Level II::{topic}")
    for front, back in cards:
        d.add_note(genanki.Note(model=MODEL, fields=[front, back], tags=[topic.replace(" ", "_")]))
        total += 1
    decks.append(d)

out = "/private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad/cfa_level2.apkg"
genanki.Package(decks).write_to_file(out)
print(f"WROTE {out}  ({total} cards across {len(decks)} topic subdecks)")
