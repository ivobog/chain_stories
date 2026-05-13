import { StatusBar } from "expo-status-bar";
import { StyleSheet, Text, View } from "react-native";

export default function App() {
  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Text style={styles.kicker}>Phase 0</Text>
      <Text style={styles.title}>Chain Stories</Text>
      <Text style={styles.body}>
        The mobile shell is ready. Authentication, rooms, live turns, and story
        play arrive in the next implementation phases.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#111827",
    padding: 24,
  },
  kicker: {
    color: "#7dd3fc",
    fontSize: 14,
    fontWeight: "700",
    letterSpacing: 0,
    marginBottom: 12,
    textTransform: "uppercase",
  },
  title: {
    color: "#f9fafb",
    fontSize: 36,
    fontWeight: "800",
    letterSpacing: 0,
    marginBottom: 16,
    textAlign: "center",
  },
  body: {
    color: "#d1d5db",
    fontSize: 17,
    lineHeight: 25,
    maxWidth: 360,
    textAlign: "center",
  },
});
