#[cfg(test)]
mod tests {
    use std::error::Error;

    #[derive(Debug)]
    struct SwatchRow {
        ppm: u32,
        l_star: f64,
        a_star: f64,
        b_star: f64,
    }

    fn parse_reference_swatches() -> Result<Vec<SwatchRow>, Box<dyn Error>> {
        let raw = std::fs::read_to_string("../data/calibration/reference-swatches.csv")?;
        let mut lines = raw.lines();
        let header = lines.next().ok_or("missing header")?;

        let columns: Vec<&str> = header.split(',').collect();
        let ppm_idx = columns.iter().position(|c| *c == "ppm").ok_or("missing ppm")?;
        let l_idx = columns
            .iter()
            .position(|c| *c == "L_star")
            .ok_or("missing L_star")?;
        let a_idx = columns
            .iter()
            .position(|c| *c == "a_star")
            .ok_or("missing a_star")?;
        let b_idx = columns
            .iter()
            .position(|c| *c == "b_star")
            .ok_or("missing b_star")?;

        let mut rows = Vec::new();
        for line in lines {
            if line.trim().is_empty() {
                continue;
            }
            let cells: Vec<&str> = line.split(',').collect();
            rows.push(SwatchRow {
                ppm: cells[ppm_idx].parse()?,
                l_star: cells[l_idx].parse()?,
                a_star: cells[a_idx].parse()?,
                b_star: cells[b_idx].parse()?,
            });
        }
        rows.sort_by_key(|r| r.ppm);
        Ok(rows)
    }

    #[test]
    fn keeps_expected_colorimetric_trend_from_0_to_500ppm() -> Result<(), Box<dyn Error>> {
        let rows = parse_reference_swatches()?;

        let ppm_0 = rows.iter().find(|r| r.ppm == 0).ok_or("missing 0 ppm")?;
        let ppm_50 = rows.iter().find(|r| r.ppm == 50).ok_or("missing 50 ppm")?;

        assert!(ppm_0.b_star >= 30.0, "0 ppm must remain strongly yellow");
        assert!(ppm_50.b_star >= 30.0, "50 ppm must remain strongly yellow");
        assert!(ppm_0.a_star <= 0.0 && ppm_50.a_star <= 0.0);

        let mut high_range: Vec<&SwatchRow> = rows
            .iter()
            .filter(|r| r.ppm >= 100 && r.ppm <= 500)
            .collect();
        high_range.sort_by_key(|r| r.ppm);

        for pair in high_range.windows(2) {
            let previous = pair[0];
            let current = pair[1];
            assert!(
                previous.b_star > current.b_star,
                "b* must decrease between {} and {} ppm",
                previous.ppm,
                current.ppm
            );
            assert!(
                previous.l_star > current.l_star,
                "L* must decrease between {} and {} ppm",
                previous.ppm,
                current.ppm
            );
        }

        Ok(())
    }
}
